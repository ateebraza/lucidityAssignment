package com.springboot.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplyOfferRequest {
    private int cart_value;
    private int restaurant_id;
    private int user_id;
}
