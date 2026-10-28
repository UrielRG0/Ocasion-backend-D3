package com.dom3.dom.repository;

import com.dom3.dom.entity.ReservacionServiciosEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservacionServiciosRepository extends JpaRepository<ReservacionServiciosEntity, Integer> {
}