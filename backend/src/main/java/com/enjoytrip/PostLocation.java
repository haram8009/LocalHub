package com.enjoytrip;

import jakarta.validation.constraints.*;

public class PostLocation {
    @Size(max=100) public String contentId;
    @Size(max=20) public String contentTypeId;
    @NotBlank @Size(max=200) public String name;
    @Size(max=100) public String category;
    @Size(max=500)  public String address;
    @Size(max=100) public String region;
    @Size(max=10000)  public String description;
    @Size(max=100) public String tel;
    @DecimalMin("-90") @DecimalMax("90") public Double latitude;
    @DecimalMin("-180") @DecimalMax("180") public Double longitude;
    @Size(max=2000)  public String thumbnailImageUrl;
    @Size(max=2000)  public String originalImageUrl;
    public Integer visitOrder;
    public PostLocation() {}
}
