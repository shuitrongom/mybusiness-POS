package com.mybusinesssilva.invoicing.adapters.in.rest;

import com.mybusinesssilva.invoicing.application.CartaPorteService;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogos del complemento Carta Porte 3.1: permisos, vehículos, remolques, operadores y
 * ubicaciones. Pantalla "Catálogos Carta Porte".
 */
@RestController
@RequestMapping("/api/v1/invoicing/carta-porte")
@PreAuthorize("@moduleAccess.canUse('invoicing')")
public class CartaPorteController {

    private final CartaPorteService cpService;

    public CartaPorteController(CartaPorteService cpService) {
        this.cpService = cpService;
    }

    @GetMapping("/permiso")
    public List<Map<String, Object>> permisos() {
        return cpService.listPermisos();
    }

    @PostMapping("/permiso")
    public Map<String, Long> addPermiso(@RequestBody PermisoRequest r) {
        return Map.of("id", cpService.addPermiso(r.tipoPermiso(), r.numero(), r.descripcion()));
    }

    @GetMapping("/vehiculo")
    public List<Map<String, Object>> vehiculos() {
        return cpService.listVehiculos();
    }

    @PostMapping("/vehiculo")
    public Map<String, Long> addVehiculo(@RequestBody VehiculoRequest r) {
        return Map.of("id", cpService.addVehiculo(r.configVehic(), r.placa(), r.anioModelo(), r.aseguradora(), r.polizaSeguro()));
    }

    @GetMapping("/remolque")
    public List<Map<String, Object>> remolques() {
        return cpService.listRemolques();
    }

    @PostMapping("/remolque")
    public Map<String, Long> addRemolque(@RequestBody RemolqueRequest r) {
        return Map.of("id", cpService.addRemolque(r.subtipo(), r.placa()));
    }

    @GetMapping("/operador")
    public List<Map<String, Object>> operadores() {
        return cpService.listOperadores();
    }

    @PostMapping("/operador")
    public Map<String, Long> addOperador(@RequestBody OperadorRequest r) {
        return Map.of("id", cpService.addOperador(r.nombre(), r.rfc(), r.curp(), r.numLicencia()));
    }

    @GetMapping("/ubicacion")
    public List<Map<String, Object>> ubicaciones() {
        return cpService.listUbicaciones();
    }

    @PostMapping("/ubicacion")
    public Map<String, Long> addUbicacion(@RequestBody CartaPorteService.UbicacionData r) {
        return Map.of("id", cpService.addUbicacion(r));
    }

    @DeleteMapping("/{catalog}/{id}")
    public Map<String, String> delete(@PathVariable String catalog, @PathVariable long id) {
        cpService.delete(catalog, id);
        return Map.of("status", "ok");
    }

    public record PermisoRequest(String tipoPermiso, String numero, String descripcion) {
    }

    public record VehiculoRequest(String configVehic, String placa, Integer anioModelo,
                                  String aseguradora, String polizaSeguro) {
    }

    public record RemolqueRequest(String subtipo, String placa) {
    }

    public record OperadorRequest(String nombre, String rfc, String curp, String numLicencia) {
    }
}
