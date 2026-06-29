package com.ventas.ms_ventas.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ventas.ms_ventas.model.Venta;

public interface VentaRepository extends JpaRepository<Venta, Long>{
    Optional<Venta> findByIdPedido(Long idPedido);
    List<Venta> findByIdSucursal(Long idSucursal);

}
