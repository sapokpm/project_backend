package com.example.shop.dto.response;

import java.util.List;

/** The cart after re-adding an old order, and the reasons any lines could not be added. */
public record ReorderResponse(CartResponse cart, List<String> skipped) {
}
