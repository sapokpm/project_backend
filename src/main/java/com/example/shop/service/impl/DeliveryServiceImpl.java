package com.example.shop.service.impl;

import com.example.shop.dto.response.DeliveryOptionResponse;
import com.example.shop.exception.BadRequestException;
import com.example.shop.mapper.OrderMapper;
import com.example.shop.model.entity.DeliveryOption;
import com.example.shop.model.enums.DeliveryMethod;
import com.example.shop.repository.DeliveryOptionRepository;
import com.example.shop.service.DeliveryQuote;
import com.example.shop.service.DeliveryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class DeliveryServiceImpl implements DeliveryService {
    private final DeliveryOptionRepository options;
    private final OrderMapper mapper;

    public DeliveryServiceImpl(DeliveryOptionRepository options, OrderMapper mapper) {
        this.options = options;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryOptionResponse> activeOptions() {
        return options.findByIsActiveTrue().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryQuote quote(DeliveryMethod method, BigDecimal subtotal) {
        // the option code is the enum name: COURIER, PICKUP_POINT, POST_OFFICE, EXPRESS
        DeliveryOption o = options.findByCode(method.name()).filter(x -> Boolean.TRUE.equals(x.getIsActive()))
                .orElseThrow(() -> new BadRequestException("Delivery method " + method + " is not available"));
        boolean free = o.getFreeThreshold() != null && subtotal.compareTo(o.getFreeThreshold()) >= 0;
        LocalDate today = LocalDate.now();
        return new DeliveryQuote(o.getCode(), o.getName(), free ? BigDecimal.ZERO.setScale(2) : o.getBasePrice(), free,
                today.plusDays(o.getEstimatedDaysMin()), today.plusDays(o.getEstimatedDaysMax()));
    }
}
