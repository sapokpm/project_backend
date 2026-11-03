package com.example.shop.delivery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Seed: COURIER 5.00 (free from 100, 2-4 days), PICKUP_POINT 2.00 (free from 50, 2-5), POST_OFFICE 3.00 (free from 80, 4-8), EXPRESS 15.00 (never free, 1 day). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeliveryApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private ResultActions calc(String method, String subtotal) throws Exception {
        return mvc.perform(post("/api/delivery/calculate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"deliveryMethod\":\"" + method + "\",\"subtotal\":" + subtotal + "}"));
    }

    @Test
    void methodsArePublicAndListOnlyActiveOptions() throws Exception {
        mvc.perform(get("/api/delivery/methods")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("COURIER", "PICKUP_POINT", "POST_OFFICE", "EXPRESS")))
                .andExpect(jsonPath("$[?(@.code=='EXPRESS')].freeThreshold", contains(nullValue())));
        jdbc.update("update delivery_options set is_active = false where code = 'POST_OFFICE'");
        mvc.perform(get("/api/delivery/methods")).andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void costIsTheBasePriceBelowTheThreshold() throws Exception {
        calc("COURIER", "99.99").andExpect(status().isOk()).andExpect(jsonPath("$.cost").value(5.0))
                .andExpect(jsonPath("$.free").value(false)).andExpect(jsonPath("$.code").value("COURIER"));
    }

    @Test
    void costIsFreeFromTheThresholdUpward() throws Exception {
        calc("COURIER", "100").andExpect(jsonPath("$.cost").value(0)).andExpect(jsonPath("$.free").value(true));
        calc("COURIER", "5000").andExpect(jsonPath("$.free").value(true));
        calc("PICKUP_POINT", "49.99").andExpect(jsonPath("$.cost").value(2.0));
        calc("PICKUP_POINT", "50").andExpect(jsonPath("$.free").value(true));
        calc("POST_OFFICE", "79.99").andExpect(jsonPath("$.cost").value(3.0));
        calc("POST_OFFICE", "80").andExpect(jsonPath("$.free").value(true));
    }

    @Test
    void expressIsNeverFree() throws Exception {
        calc("EXPRESS", "100000").andExpect(jsonPath("$.cost").value(15.0)).andExpect(jsonPath("$.free").value(false));
    }

    @Test
    void estimatedDatesComeFromTheOptionsDayRange() throws Exception {
        LocalDate today = LocalDate.now();
        calc("COURIER", "10").andExpect(jsonPath("$.estimatedFrom").value(today.plusDays(2).toString()))
                .andExpect(jsonPath("$.estimatedTo").value(today.plusDays(4).toString()));
        calc("EXPRESS", "10").andExpect(jsonPath("$.estimatedFrom").value(today.plusDays(1).toString()))
                .andExpect(jsonPath("$.estimatedTo").value(today.plusDays(1).toString()));
    }

    @Test
    void badInputIsRefused() throws Exception {
        calc("TELEPORT", "10").andExpect(status().isBadRequest());
        calc("COURIER", "-1").andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.subtotal").exists());
        mvc.perform(post("/api/delivery/calculate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.deliveryMethod").exists());
        jdbc.update("update delivery_options set is_active = false where code = 'COURIER'");
        calc("COURIER", "10").andExpect(status().isBadRequest());
    }
}
