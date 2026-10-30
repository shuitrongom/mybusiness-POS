package com.mybusinesssilva.inventory.rawmaterial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mybusinesssilva.inventory.rawmaterial.application.RawMaterialService;
import com.mybusinesssilva.inventory.rawmaterial.domain.model.RawMaterial;
import com.mybusinesssilva.inventory.rawmaterial.domain.model.RawMaterialInUseException;
import com.mybusinesssilva.inventory.rawmaterial.domain.port.out.RawMaterialRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit del servicio de materia prima: borrado duro condicionado (barrera dura en backend),
 * revalidación de nombre al reactivar y unicidad de nombre en la edición excluyendo el propio id.
 * El repositorio se mockea; no toca base de datos.
 */
class RawMaterialServiceTest {

    private RawMaterialRepository repository;
    private RawMaterialService service;

    private static RawMaterial material(long id) {
        return new RawMaterial(id, null, "Harina", "kg",
                new BigDecimal("18"), new BigDecimal("5"), true);
    }

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(RawMaterialRepository.class);
        service = new RawMaterialService(repository);
    }

    @Test
    void deleteRejectsWhenHasStock() {
        when(repository.find(1L)).thenReturn(Optional.of(material(1L)));
        when(repository.hasAnyStock(1L)).thenReturn(true);
        when(repository.hasMovements(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(RawMaterialInUseException.class)
                .satisfies(ex -> {
                    RawMaterialInUseException e = (RawMaterialInUseException) ex;
                    assertThat(e.hasStock()).isTrue();
                    assertThat(e.hasMovements()).isFalse();
                });
        verify(repository, never()).hardDelete(anyLong());
    }

    @Test
    void deleteRejectsWhenHasMovements() {
        when(repository.find(1L)).thenReturn(Optional.of(material(1L)));
        when(repository.hasAnyStock(1L)).thenReturn(false);
        when(repository.hasMovements(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(RawMaterialInUseException.class);
        verify(repository, never()).hardDelete(anyLong());
    }

    @Test
    void deleteRemovesVirginMaterial() {
        when(repository.find(1L)).thenReturn(Optional.of(material(1L)));
        when(repository.hasAnyStock(1L)).thenReturn(false);
        when(repository.hasMovements(1L)).thenReturn(false);

        service.delete(1L);

        verify(repository).hardDelete(1L);
    }

    @Test
    void setActiveTrueRejectsDuplicateName() {
        when(repository.find(1L)).thenReturn(Optional.of(material(1L)));
        when(repository.existsActiveName("Harina", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.setActive(1L, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe un insumo activo con ese nombre");
        verify(repository, never()).setActive(anyLong(), eq(true));
    }

    @Test
    void setActiveFalseDoesNotRevalidateName() {
        when(repository.find(1L)).thenReturn(Optional.of(material(1L)));

        service.setActive(1L, false);

        verify(repository).setActive(1L, false);
        verify(repository, never()).existsActiveName(any(), anyLong());
    }

    @Test
    void updateRespectsUniqueNameExcludingOwnId() {
        when(repository.find(1L)).thenReturn(Optional.of(material(1L)));
        when(repository.existsActiveName("Harina", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, material(1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe un insumo activo con ese nombre");
        verify(repository, never()).update(anyLong(), any());
    }
}
