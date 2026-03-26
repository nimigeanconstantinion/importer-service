package com.example.importer.scheduler;


import com.example.importer.model.MapStocOptim;
import com.example.importer.service.MapStocOptService;
import com.example.importer.service.MessagePublisherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class MapStocOptimImportScheduler {

    private final MapStocOptService mapStocOptService;
    private final MessagePublisherService messagePublisherService;

    public MapStocOptimImportScheduler(MapStocOptService mapStocOptService,MessagePublisherService messagePublisherService) {
        this.mapStocOptService = mapStocOptService;
        this.messagePublisherService=messagePublisherService;
    }

    @Scheduled(fixedDelay = 600000)
    public void runImport() {
        log.info("Triggering scheduled product import");
        List<MapStocOptim> lista=mapStocOptService.getAllMapStocOpt();
        for(MapStocOptim m:lista){
            messagePublisherService.publishCreate(m);
        }
    }
}