package com.dom3.dom.repository;

import com.dom3.dom.entity.PagosEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PagosRepository extends JpaRepository<PagosEntity, Integer> {
    //validacion de cuplicados
    @Query("SELECT COUNT(p) > 0 FROM PagosEntity p WHERE p.id_transaccion_pasarela = :txId")
    boolean existeTransaccion(@Param("txId") String txId);

    @Query("SELECT COUNT(p) > 0 FROM PagosEntity p WHERE p.id_transaccion_pasarela = :referencia")
    boolean existsByIdTransaccionPasarela(@Param("referencia") String referencia);
}