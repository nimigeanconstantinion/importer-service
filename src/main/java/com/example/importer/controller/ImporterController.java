package com.example.importer.controller;

import com.example.importer.model.MapStocOptim;
import com.example.importer.service.MapStocOptService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
//import java.util.logging.Logger;
import lombok.extern.slf4j.Slf4j;

import net.logstash.logback.argument.StructuredArguments.*;

import static net.logstash.logback.argument.StructuredArguments.*;

@RestController
@RequestMapping("/api/v1/import")
//@CrossOrigin
public class ImporterController {

    private MapStocOptService mapStocOptService;
    private static final Logger log = LoggerFactory.getLogger(ImporterController.class);
    public ImporterController(MapStocOptService mapStocOptService) {
        this.mapStocOptService = mapStocOptService;
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("")
    public ResponseEntity<List<MapStocOptim>> getAll(HttpServletRequest request){

        List<MapStocOptim> lista=mapStocOptService.getAllMapStocOpt();
        log.info("Create message request received",
                keyValue("eventType", "IMPORTER_MESSAGE"),
                keyValue("importedBulk", "IMPORTED"),
                value("sizeList", lista.size()));

        return ResponseEntity.ok(lista);
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/byid/{ida}")
    public ResponseEntity<List<MapStocOptim>> getById(@PathVariable String ida) {

        try{
            List<MapStocOptim> mp=mapStocOptService.getMapStocOptByIDArticol(ida);
            return ResponseEntity.ok(mp);
        }catch (RuntimeException e){
            throw e;
        }


    }

}
