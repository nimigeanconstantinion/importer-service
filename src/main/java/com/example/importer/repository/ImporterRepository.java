package com.example.importer.repository;

import com.example.importer.model.MapStocOptim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ImporterRepository extends JpaRepository<MapStocOptim,Integer> {
    @Query(value = "select m from MapStoc m where trim(m.idIntern)=?1")
    List<MapStocOptim> findByIdArticol(String idA);
}
