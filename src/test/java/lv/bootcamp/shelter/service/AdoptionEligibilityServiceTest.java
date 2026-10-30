package lv.bootcamp.shelter.service;

import lv.bootcamp.shelter.audit.AuditLogger;
import lv.bootcamp.shelter.audit.RejectionReason;
import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.model.Adopter;
import lv.bootcamp.shelter.model.AdoptionResult;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.repository.AdopterRepository;
import lv.bootcamp.shelter.repository.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdoptionEligibilityServiceTest {

    @Mock
    private AdopterRepository adopterRepository;
    @Mock
    private AnimalRepository animalRepository;
    @Mock
    private NotificationClient notificationClient;
    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private AdoptionEligibilityService service;

    // ---------- evaluateAdoption: rejection branches ----------

    @Test
    void evaluate_rejectsWhenAdopterNotFound() {
        when(adopterRepository.findById(1L)).thenReturn(Optional.empty());

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.ADOPTER_NOT_FOUND);
        verifyNoInteractions(auditLogger, notificationClient);
    }

    @Test
    void evaluate_rejectsWhenAnimalNotFound() {
        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter(30, 0, 0, false, false)));
        when(animalRepository.findById(2L)).thenReturn(Optional.empty());

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.reason()).isEqualTo(RejectionReasons.ANIMAL_NOT_FOUND);
    }

    @Test
    void evaluate_rejectsWhenAnimalNotAvailable() {
        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter(30, 0, 0, false, false)));
        when(animalRepository.findById(2L)).thenReturn(Optional.of(animal(AnimalType.DOG, AnimalStatus.ADOPTED, 3)));

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.reason()).isEqualTo(RejectionReasons.ANIMAL_NOT_AVAILABLE);
    }

    @Test
    void evaluate_rejectsUnderageAndLogsRejection() {
        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter(17, 0, 0, false, false)));
        when(animalRepository.findById(2L)).thenReturn(Optional.of(animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 3)));

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.reason()).isEqualTo(RejectionReasons.UNDERAGE);
        verify(auditLogger).logRejection(1L, 2L, RejectionReason.UNDERAGE);
    }

    @Test
    void evaluate_rejectsWhenPetLimitReachedForNormalProperty() {
        // 3 pets, normal property -> limit is 3 -> rejected
        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter(30, 3, 0, false, false)));
        when(animalRepository.findById(2L)).thenReturn(Optional.of(animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 3)));

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.reason()).isEqualTo(RejectionReasons.PET_LIMIT_REACHED);
        verify(auditLogger).logRejection(1L, 2L, RejectionReason.PET_LIMIT_REACHED);
    }

    @Test
    void evaluate_allowsMorePetsForLargeProperty() {
        // 3 pets BUT large property -> limit is 5 -> NOT rejected -> approved
        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter(30, 3, 0, true, false)));
        when(animalRepository.findById(2L)).thenReturn(Optional.of(animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 3)));

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.approved()).isTrue();
    }

    @Test
    void evaluate_rejectsExoticAnimalWithoutPermit() {
        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter(30, 0, 0, false, false)));
        when(animalRepository.findById(2L)).thenReturn(Optional.of(animal(AnimalType.BIRD, AnimalStatus.AVAILABLE, 3)));

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.reason()).isEqualTo(RejectionReasons.EXOTIC_PERMIT_REQUIRED);
        verify(auditLogger).logRejection(1L, 2L, RejectionReason.NO_EXOTIC_PERMIT);
    }

    @Test
    void evaluate_rejectsRabbitWithoutPermit() {
        // RABBIT is also exotic — covers the second equality check in isExoticAnimal
        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter(30, 0, 0, false, false)));
        when(animalRepository.findById(2L)).thenReturn(Optional.of(animal(AnimalType.RABBIT, AnimalStatus.AVAILABLE, 3)));

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.reason()).isEqualTo(RejectionReasons.EXOTIC_PERMIT_REQUIRED);
        verify(auditLogger).logRejection(1L, 2L, RejectionReason.NO_EXOTIC_PERMIT);
    }

    @Test
    void evaluate_approvesExoticAnimalWithPermit() {
        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter(30, 0, 0, false, true)));
        when(animalRepository.findById(2L)).thenReturn(Optional.of(animal(AnimalType.RABBIT, AnimalStatus.AVAILABLE, 3)));

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.approved()).isTrue();
    }

    @Test
    void evaluate_approvesEligibleAdopterAndNotifies() {
        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter(18, 0, 0, false, false)));
        when(animalRepository.findById(2L)).thenReturn(Optional.of(animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 3)));

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.approved()).isTrue();
        verify(auditLogger).logApproval(eq(1L), eq(2L), anyInt());
        verify(notificationClient).sendApprovalNotification("anna@example.com", "Rex");
    }

    // ---------- calculatePriorityScore: score branches & boundaries ----------

    @Test
    void score_isZeroForBaselineAdopter() {
        int score = service.calculatePriorityScore(
                adopter(30, 0, 0, false, false), animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 3));
        assertThat(score).isZero();
    }

    @Test
    void score_addsForPreviousAdoptions() {
        // exactly 1 previous adoption -> +10 (boundary for > 0)
        assertThat(service.calculatePriorityScore(
                adopter(30, 0, 1, false, false), animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 3)))
                .isEqualTo(10);
        // exactly 3 -> still only +10 (boundary for > 3)
        assertThat(service.calculatePriorityScore(
                adopter(30, 0, 3, false, false), animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 3)))
                .isEqualTo(10);
        // 4 -> +10 +5
        assertThat(service.calculatePriorityScore(
                adopter(30, 0, 4, false, false), animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 3)))
                .isEqualTo(15);
    }

    @Test
    void score_addsForLargeProperty() {
        assertThat(service.calculatePriorityScore(
                adopter(30, 0, 0, true, false), animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 3)))
                .isEqualTo(15);
    }

    @Test
    void score_addsForSeniorAnimalWithBoundaryAndNull() {
        // age 8 (> 7) -> +20
        assertThat(service.calculatePriorityScore(
                adopter(30, 0, 0, false, false), animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 8)))
                .isEqualTo(20);
        // age 7 (boundary, NOT > 7) -> 0
        assertThat(service.calculatePriorityScore(
                adopter(30, 0, 0, false, false), animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 7)))
                .isZero();
        // null age -> no bonus, no NullPointerException
        assertThat(service.calculatePriorityScore(
                adopter(30, 0, 0, false, false), animal(AnimalType.DOG, AnimalStatus.AVAILABLE, null)))
                .isZero();
    }

    @Test
    void score_subtractsForCurrentPetsAndNeverGoesNegative() {
        // baseline 0, 1 pet -> 0 - 2 = -2 -> clamped to 0
        assertThat(service.calculatePriorityScore(
                adopter(30, 1, 0, false, false), animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 3)))
                .isZero();
        // large property (+15), 2 pets (-4) -> 11
        assertThat(service.calculatePriorityScore(
                adopter(30, 2, 0, true, false), animal(AnimalType.DOG, AnimalStatus.AVAILABLE, 3)))
                .isEqualTo(11);
    }

    // ---------- helpers ----------

    private Adopter adopter(int age, int petCount, int previousAdoptions,
                            boolean largeProperty, boolean exoticPermit) {
        Adopter a = new Adopter();
        a.setId(1L);
        a.setName("Anna");
        a.setEmail("anna@example.com");
        a.setAge(age);
        a.setCurrentPetCount(petCount);
        a.setPreviousAdoptions(previousAdoptions);
        a.setLargeProperty(largeProperty);
        a.setExoticPermit(exoticPermit);
        return a;
    }

    private Animal animal(AnimalType type, AnimalStatus status, Integer age) {
        Animal an = new Animal();
        an.setId(2L);
        an.setName("Rex");
        an.setType(type);
        an.setStatus(status);
        an.setAge(age);
        return an;
    }
}
