package com.example.importer.service;

import com.example.importer.model.MapStocOptim;
import com.example.importer.repository.ImporterRepository;
import com.example.importer.repository.StocOptimRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class MapStocOptImplService implements MapStocOptService{

    private ImporterRepository stocOptimRepo;

    public MapStocOptImplService(ImporterRepository stocOptimRepo) {
        this.stocOptimRepo = stocOptimRepo;
    }

    @Override
    public List<MapStocOptim> getAllMapStocOpt() {
        List<MapStocOptim> lista=new ArrayList<>();
        lista=stocOptimRepo.findAll();
        return lista;
    }

    @Override
    public List<MapStocOptim> getMapStocOptByIDArticol(String idA) {
        List<MapStocOptim> opm= stocOptimRepo.findByIdArticol(idA);
        if(opm.size()==0){
//            log.error("querySrvFailID: "+idA);
            throw new RuntimeException("ID-ul nu exista!!");
        }
        return opm;
    }
}
