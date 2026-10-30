package lv.bootcamp.shelter.repository;

import lv.bootcamp.shelter.model.AdoptionRecord;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AdoptionRecordRepositoryTest {

    @Autowired
    private AdoptionRecordRepository adoptionRecordRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findRecentAdoptionsByAnimalType_shouldFilterByTypeAndDate() {
        LocalDateTime now = LocalDateTime.now();

        // Совпадает: DOG, недавно
        persistAdoption(newAnimal("Rex", AnimalType.DOG), "anna@example.com", now.minusDays(1));
        // Не тот тип: CAT — НЕ должен попасть
        persistAdoption(newAnimal("Mia", AnimalType.CAT), "bob@example.com", now.minusDays(1));
        // Слишком старое: DOG, но 30 дней назад — НЕ должен попасть
        persistAdoption(newAnimal("Old", AnimalType.DOG), "carl@example.com", now.minusDays(30));
        entityManager.flush();

        var recentDogs = adoptionRecordRepository.findRecentAdoptionsByAnimalType(
                AnimalType.DOG, now.minusDays(7));

        assertThat(recentDogs).hasSize(1);
        assertThat(recentDogs.getFirst().getAnimal().getName()).isEqualTo("Rex");
    }

    private void persistAdoption(Animal animal, String email, LocalDateTime adoptedAt) {
        entityManager.persist(animal);
        AdoptionRecord record = new AdoptionRecord();
        record.setAnimal(animal);
        record.setAdopterEmail(email);
        record.setAdoptedAt(adoptedAt);
        entityManager.persist(record);
    }

    private Animal newAnimal(String name, AnimalType type) {
        Animal animal = new Animal();
        animal.setName(name);
        animal.setType(type);
        animal.setStatus(AnimalStatus.AVAILABLE);
        return animal;
    }
}
