package com.dom3.dom.repository;

import com.dom3.dom.entity.ReservacionInventarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservacionInventarioRepository extends JpaRepository<ReservacionInventarioEntity, Integer> {
}