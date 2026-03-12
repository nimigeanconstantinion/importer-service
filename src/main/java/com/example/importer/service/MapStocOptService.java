package com.example.importer.service;

import com.example.importer.model.MapStocOptim;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface MapStocOptService {

    List<MapStocOptim> getAllMapStocOpt();

    List<MapStocOptim> getMapStocOptByIDArticol(String id);

}
