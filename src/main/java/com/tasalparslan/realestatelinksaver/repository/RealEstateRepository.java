package com.tasalparslan.realestatelinksaver.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tasalparslan.realestatelinksaver.model.RealEstate;

public interface RealEstateRepository extends MongoRepository<RealEstate, String> {

}