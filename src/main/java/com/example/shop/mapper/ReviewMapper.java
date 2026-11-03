package com.example.shop.mapper;

import com.example.shop.dto.request.ReviewRequest;
import com.example.shop.dto.response.ReviewResponse;
import com.example.shop.model.entity.Review;
import com.example.shop.model.entity.User;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {

    public ReviewResponse toResponse(Review r) {
        User u = r.getUser();
        // first name + last initial only: do not publish full customer names
        String author = u.getFirstName() + " " + (u.getLastName().isEmpty() ? "" : u.getLastName().charAt(0) + ".");
        return new ReviewResponse(r.getId(), r.getProduct().getId(), author.trim(), r.getRating(), r.getTitle(),
                r.getComment(), r.getPros(), r.getCons(), Boolean.TRUE.equals(r.getIsVerifiedPurchase()),
                r.getHelpfulCount(), r.getNotHelpfulCount(), r.getAdminReply(), r.getStatus(), r.getCreatedAt());
    }

    /** Only the fields a client may set. Status, verified flag and counters stay with the service. */
    public void apply(ReviewRequest req, Review r) {
        r.setRating(req.rating());
        r.setTitle(req.title());
        r.setComment(req.comment());
        r.setPros(req.pros());
        r.setCons(req.cons());
    }
}
