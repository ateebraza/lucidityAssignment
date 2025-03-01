package com.springboot.controller;

public class TestAutowiredController extends AutowiredController {
    // Override getSegmentResponse() to return a fixed SegmentResponse for testing.
    @Override
    public SegmentResponse getSegmentResponse(int userId) {
        return new SegmentResponse("p1");
    }
}
