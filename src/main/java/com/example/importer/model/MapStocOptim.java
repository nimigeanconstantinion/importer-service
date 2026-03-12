package com.example.importer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

@Entity(name = "MapStoc")
@Table(name = "map_stoc_optim")

public class MapStocOptim {
    @Id
    private int id;

    @Column(name="id_intern")
    private String idIntern;

    @Column(name = "articol")
    private String articol;

    @Column(name = "categorie")
    private String categorie;

    @Column(name = "grupa")
    private String grupa;

    @Column(name = "idFurn")
    private int id_furn;

    @Column(name = "furniz")
    private String furniz;

    @Column(name = "nrZile")
    private int nr_zile;

    public String toString(){
        return "id="+this.id+";"+"denumire="+this.articol+";"+"categorie="+this.categorie+";"+"furnizoe="+this.furniz+
                ";"+"nr_zile="+this.nr_zile;
    }
}
