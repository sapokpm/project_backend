package com.example.shop.service.impl;

import com.example.shop.dto.request.ProcessPaymentRequest;
import com.example.shop.dto.response.PaymentMethodResponse;
import com.example.shop.dto.response.PaymentResponse;
import com.example.shop.dto.response.PaymentStatusResponse;
import com.example.shop.exception.BadRequestException;
import com.example.shop.exception.InvalidStatusTransitionException;
import com.example.shop.exception.PaymentFailedException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.mapper.OrderMapper;
import com.example.shop.model.entity.Order;
import com.example.shop.model.entity.Payment;
import com.example.shop.model.enums.OrderStatus;
import com.example.shop.model.enums.PaymentMethod;
import com.example.shop.model.enums.PaymentStatus;
import com.example.shop.repository.OrderRepository;
import com.example.shop.service.OrderStateService;
import com.example.shop.service.PaymentService;
import com.example.shop.service.payment.PaymentGateway;
import com.example.shop.util.LuhnValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Three phases, so no database lock is held while the (slow) gateway answers:
 * A) check and mark the payment PROCESSING, B) ask the gateway, C) record the result.
 */
@Service
public class PaymentServiceImpl implements PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    /** A PROCESSING payment older than this is treated as abandoned (the server died mid-payment). */
    private static final Duration STALE_AFTER = Duration.ofMinutes(2);

    private final OrderRepository orders;
    private final OrderStateService state;
    private final PaymentGateway gateway;
    private final OrderMapper mapper;
    private final TransactionTemplate tx;
    private final ObjectMapper json;

    public PaymentServiceImpl(OrderRepository orders, OrderStateService state, PaymentGateway gateway,
                              OrderMapper mapper, PlatformTransactionManager txManager, ObjectMapper json) {
        this.json = json;
        this.orders = orders;
        this.state = state;
        this.gateway = gateway;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public PaymentResponse process(Long userId, ProcessPaymentRequest r) {
        // 0. cheap checks first: nothing is read or changed if the card is malformed
        validateInput(r);

        // A. claim the order
        Attempt attempt = tx.execute(s -> begin(userId, r));
        if (attempt.method() == PaymentMethod.CASH_ON_DELIVERY) {
            return attempt.immediate(); // nothing to charge now: the order is confirmed and awaits payment on delivery
        }

        // B. the gateway call, outside any transaction
        PaymentGateway.Result result;
        try {
            result = gateway.charge(new PaymentGateway.Charge(attempt.method(), r.cardNumber(), attempt.amount()));
        } catch (RuntimeException e) {
            log.error("Payment gateway failed for {}", attempt.transactionId(), e);
            result = PaymentGateway.Result.declined("GATEWAY_ERROR", "The payment service is unavailable; try again");
        }

        // C. record the outcome
        PaymentGateway.Result outcome = result;
        PaymentResponse response = tx.execute(s -> finish(attempt, outcome));
        log.info("Payment {} for order {}: {}", attempt.transactionId(), attempt.orderId(), response.status());
        if (response.status() != PaymentStatus.COMPLETED) {
            throw new PaymentFailedException(response.errorMessage());
        }
        return response;
    }

    // -------------------------------------------------------------- phase A

    private record Attempt(Long orderId, PaymentMethod method, BigDecimal amount, String transactionId,
                           PaymentResponse immediate) {
    }

    private void validateInput(ProcessPaymentRequest r) {
        if (r.paymentMethod() == PaymentMethod.BONUS_POINTS) {
            throw new BadRequestException("Bonus points are not available as a payment method");
        }
        if (r.paymentMethod() != PaymentMethod.CARD) return;
        if (r.cardNumber() == null || r.expiry() == null || r.cvv() == null) {
            throw new BadRequestException("Card payments need cardNumber, expiry and cvv");
        }
        if (!LuhnValidator.isValid(r.cardNumber())) {
            throw new BadRequestException("Card number is not valid");
        }
        int month = Integer.parseInt(r.expiry().substring(0, 2));
        int year = 2000 + Integer.parseInt(r.expiry().substring(3, 5));
        if (YearMonth.of(year, month).isBefore(YearMonth.now())) {
            throw new BadRequestException("Card has expired");
        }
    }

    private Attempt begin(Long userId, ProcessPaymentRequest r) {
        Order o = orders.findByIdAndUserId(r.orderId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("No order with id " + r.orderId()));
        o = orders.findByIdForUpdate(o.getId()).orElseThrow(); // serialises two payments for the same order

        if (o.getStatus() == OrderStatus.PAID) {
            throw new InvalidStatusTransitionException("This order is already paid");
        }
        if (o.getStatus() != OrderStatus.PENDING && o.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidStatusTransitionException("An order that is " + o.getStatus() + " cannot be paid");
        }
        Payment p = o.getPayment();
        if (p != null && p.getStatus() == PaymentStatus.COMPLETED) {
            throw new InvalidStatusTransitionException("This order is already paid");
        }
        if (p != null && p.getStatus() == PaymentStatus.PROCESSING && !isStale(p)) {
            throw new InvalidStatusTransitionException("A payment for this order is already in progress");
        }

        if (o.getStatus() == OrderStatus.PENDING) {
            o = state.apply(o.getId(), OrderStatus.CONFIRMED); // the order is accepted; paying is what is still open
        }
        if (p == null) {
            p = new Payment();
            p.setOrder(o);
            o.setPayment(p);
        }
        p.setPaymentMethod(r.paymentMethod());
        p.setAmount(o.getTotalAmount()); // always the order total
        p.setCurrency("USD");
        p.setTransactionId("TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        p.setCardLastFourDigits(r.paymentMethod() == PaymentMethod.CARD
                ? r.cardNumber().substring(r.cardNumber().length() - 4) : null); // the only part of a card we keep
        p.setBonusPointsUsed(0);
        p.setErrorCode(null);
        p.setErrorMessage(null);
        p.setPaidAt(null);
        p.setRefundedAmount(BigDecimal.ZERO);
        p.setMetadata("{\"startedAt\":\"" + Instant.now() + "\"}");
        p.setStatus(r.paymentMethod() == PaymentMethod.CASH_ON_DELIVERY ? PaymentStatus.PENDING : PaymentStatus.PROCESSING);
        orders.saveAndFlush(o);

        PaymentResponse immediate = r.paymentMethod() == PaymentMethod.CASH_ON_DELIVERY ? mapper.toResponse(p) : null;
        return new Attempt(o.getId(), r.paymentMethod(), p.getAmount(), p.getTransactionId(), immediate);
    }

    private boolean isStale(Payment p) {
        try {
            // parsed, not string-searched: Postgres rewrites the JSON text (it adds a space after the colon)
            Instant started = Instant.parse(json.readTree(p.getMetadata()).get("startedAt").asText());
            return started.isBefore(Instant.now().minus(STALE_AFTER));
        } catch (Exception e) {
            return true; // no usable start time: do not block the customer forever
        }
    }

    // -------------------------------------------------------------- phase C

    private PaymentResponse finish(Attempt a, PaymentGateway.Result result) {
        Order o = orders.findByIdForUpdate(a.orderId()).orElseThrow();
        Payment p = o.getPayment();
        if (!a.transactionId().equals(p.getTransactionId())) {
            // another attempt took over after this one went stale: this result no longer counts
            return failed(p, "SUPERSEDED", "This payment attempt was replaced by a newer one", false);
        }
        if (result.success() && o.getStatus() != OrderStatus.CONFIRMED) {
            // cancelled (or otherwise changed) while the bank was answering: do not mark an unpayable order PAID
            return failed(p, "ORDER_NOT_PAYABLE", "The order changed while the payment was processing; you were not charged", true);
        }
        if (!result.success()) {
            return failed(p, result.errorCode(), result.errorMessage(), true);
        }
        p.setStatus(PaymentStatus.COMPLETED);
        p.setPaidAt(LocalDateTime.now());
        state.apply(o.getId(), OrderStatus.PAID);
        return mapper.toResponse(p);
    }

    private PaymentResponse failed(Payment p, String code, String message, boolean ownAttempt) {
        if (ownAttempt) { // a superseded attempt must not overwrite the newer attempt's row
            p.setStatus(PaymentStatus.FAILED);
            p.setErrorCode(code);
            p.setErrorMessage(message);
        }
        PaymentResponse r = mapper.toResponse(p);
        return ownAttempt ? r : new PaymentResponse(r.id(), r.orderId(), r.transactionId(), r.paymentMethod(),
                PaymentStatus.FAILED, r.amount(), r.currency(), r.cardLastFourDigits(), null, message, r.refundedAmount());
    }

    // ---------------------------------------------------------------- reads

    @Override
    @Transactional(readOnly = true)
    public PaymentStatusResponse status(Long userId, Long orderId) {
        Order o = orders.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("No order with id " + orderId));
        Payment p = o.getPayment();
        return new PaymentStatusResponse(o.getId(), o.getStatus(), p == null ? null : p.getStatus(),
                p == null ? null : mapper.toResponse(p));
    }

    @Override
    public List<PaymentMethodResponse> methods() {
        // BONUS_POINTS is left out until the bonus module gives customers a balance
        return List.of(
                new PaymentMethodResponse(PaymentMethod.CARD, "Credit or debit card"),
                new PaymentMethodResponse(PaymentMethod.PAYPAL, "PayPal"),
                new PaymentMethodResponse(PaymentMethod.CASH_ON_DELIVERY, "Cash on delivery"));
    }
}
