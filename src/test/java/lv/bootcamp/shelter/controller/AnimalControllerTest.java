package lv.bootcamp.shelter.controller;

import lv.bootcamp.shelter.config.SecurityConfig;
import lv.bootcamp.shelter.dto.AnimalResponse;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.service.AnimalNotFoundException;
import lv.bootcamp.shelter.service.AnimalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnimalController.class)
@Import(SecurityConfig.class)
class AnimalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnimalService animalService;

    @Test
    void findById_shouldReturn404WhenNotFound() throws Exception {
        when(animalService.findById(99L)).thenThrow(new AnimalNotFoundException(99L));

        mockMvc.perform(get("/api/animals/99"))
                .andExpect(status().isNotFound());
    }

    // ---------- ANIMAL-2: новые тесты ----------

    @Test
    void findAll_shouldReturnAnimalsAsJson() throws Exception {
        when(animalService.findAll()).thenReturn(List.of(
                new AnimalResponse(1L, "Rex", AnimalType.DOG, "Labrador", 3, "Friendly", AnimalStatus.AVAILABLE),
                new AnimalResponse(2L, "Mia", AnimalType.CAT, "Siamese", 2, "Calm", AnimalStatus.ADOPTED)
        ));

        mockMvc.perform(get("/api/animals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Rex"))
                .andExpect(jsonPath("$[1].status").value("ADOPTED"));
    }

    @Test
    void create_shouldReturn400WhenRequestBodyIsInvalid() throws Exception {
        mockMvc.perform(post("/api/animals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "type": null, "age": 3}
                                """))
                .andExpect(status().isBadRequest());

        verify(animalService, never()).create(any());
    }
}