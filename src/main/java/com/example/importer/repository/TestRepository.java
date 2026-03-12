package com.example.importer.repository;

import com.example.importer.model.MapStocOptim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TestRepository extends JpaRepository<MapStocOptim,Integer> {

    @Query(value = "select m from MapStoc m where m.idIntern=?1")
    Optional<MapStocOptim> findByIdArticol(String idA);

}
