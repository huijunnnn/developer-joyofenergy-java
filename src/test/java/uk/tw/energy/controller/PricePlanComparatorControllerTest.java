package uk.tw.energy.controller;

import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import uk.tw.energy.domain.ElectricityReading;
import uk.tw.energy.domain.PricePlan;
import uk.tw.energy.service.AccountService;
import uk.tw.energy.service.MeterReadingService;
import uk.tw.energy.service.PricePlanService;

import java.math.BigDecimal;
import java.time.*;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static uk.tw.energy.controller.PricePlanComparatorController.PRICE_PLAN_ID_KEY;



public class PricePlanComparatorControllerTest {

    private static final String PRICE_PLAN_1_ID = "test-supplier";
    private static final String PRICE_PLAN_2_ID = "best-supplier";
    private static final String PRICE_PLAN_3_ID = "second-best-supplier";
    private static final String SMART_METER_ID = "smart-meter-id";
    private PricePlanComparatorController controller;
    private MeterReadingService meterReadingService;
    private AccountService accountService;

    @BeforeEach
    public void setUp() {
        meterReadingService = new MeterReadingService(new HashMap<>());
        PricePlan pricePlan1 = new PricePlan(PRICE_PLAN_1_ID, null, BigDecimal.TEN, null);
        PricePlan pricePlan2 = new PricePlan(PRICE_PLAN_2_ID, null, BigDecimal.ONE, null);
        PricePlan pricePlan3 = new PricePlan(PRICE_PLAN_3_ID, null, BigDecimal.valueOf(2), null);

        List<PricePlan> pricePlans = Arrays.asList(pricePlan1, pricePlan2, pricePlan3);
        PricePlanService tariffService = new PricePlanService(pricePlans, meterReadingService);

        Map<String, String> meterToTariffs = new HashMap<>();
        meterToTariffs.put(SMART_METER_ID, PRICE_PLAN_1_ID);
        accountService = new AccountService(meterToTariffs);

        controller = new PricePlanComparatorController(tariffService, accountService);
    }

    @Test
    public void shouldCalculateCostForMeterReadingsForEveryPricePlan() {

        ElectricityReading electricityReading = new ElectricityReading(Instant.now().minusSeconds(3600), BigDecimal.valueOf(15.0));
        ElectricityReading otherReading = new ElectricityReading(Instant.now(), BigDecimal.valueOf(5.0));
        meterReadingService.storeReadings(SMART_METER_ID, Arrays.asList(electricityReading, otherReading));

        Map<String, BigDecimal> expectedPricePlanToCost = new HashMap<>();
        expectedPricePlanToCost.put(PRICE_PLAN_1_ID, BigDecimal.valueOf(100.0));
        expectedPricePlanToCost.put(PRICE_PLAN_2_ID, BigDecimal.valueOf(10.0));
        expectedPricePlanToCost.put(PRICE_PLAN_3_ID, BigDecimal.valueOf(20.0));

        Map<String, Object> expected = new HashMap<>();
        expected.put(PricePlanComparatorController.PRICE_PLAN_ID_KEY, PRICE_PLAN_1_ID);
        expected.put(PricePlanComparatorController.PRICE_PLAN_COMPARISONS_KEY, expectedPricePlanToCost);
        assertThat(controller.calculatedCostForEachPricePlan(SMART_METER_ID).getBody()).isEqualTo(expected);
    }

    @Test
    public void shouldRecommendCheapestPricePlansNoLimitForMeterUsage() throws Exception {

        ElectricityReading electricityReading = new ElectricityReading(Instant.now().minusSeconds(1800), BigDecimal.valueOf(35.0));
        ElectricityReading otherReading = new ElectricityReading(Instant.now(), BigDecimal.valueOf(3.0));
        meterReadingService.storeReadings(SMART_METER_ID, Arrays.asList(electricityReading, otherReading));

        List<Map.Entry<String, BigDecimal>> expectedPricePlanToCost = new ArrayList<>();
        expectedPricePlanToCost.add(new AbstractMap.SimpleEntry<>(PRICE_PLAN_2_ID, BigDecimal.valueOf(38.0)));
        expectedPricePlanToCost.add(new AbstractMap.SimpleEntry<>(PRICE_PLAN_3_ID, BigDecimal.valueOf(76.0)));
        expectedPricePlanToCost.add(new AbstractMap.SimpleEntry<>(PRICE_PLAN_1_ID, BigDecimal.valueOf(380.0)));

        assertThat(controller.recommendCheapestPricePlans(SMART_METER_ID, null).getBody()).isEqualTo(expectedPricePlanToCost);
    }


    @Test
    public void shouldRecommendLimitedCheapestPricePlansForMeterUsage() throws Exception {

        ElectricityReading electricityReading = new ElectricityReading(Instant.now().minusSeconds(2700), BigDecimal.valueOf(5.0));
        ElectricityReading otherReading = new ElectricityReading(Instant.now(), BigDecimal.valueOf(20.0));
        meterReadingService.storeReadings(SMART_METER_ID, Arrays.asList(electricityReading, otherReading));

        List<Map.Entry<String, BigDecimal>> expectedPricePlanToCost = new ArrayList<>();
        expectedPricePlanToCost.add(new AbstractMap.SimpleEntry<>(PRICE_PLAN_2_ID, BigDecimal.valueOf(16.7)));
        expectedPricePlanToCost.add(new AbstractMap.SimpleEntry<>(PRICE_PLAN_3_ID, BigDecimal.valueOf(33.4)));

        assertThat(controller.recommendCheapestPricePlans(SMART_METER_ID, 2).getBody()).isEqualTo(expectedPricePlanToCost);
    }

    @Test
    public void shouldRecommendCheapestPricePlansMoreThanLimitAvailableForMeterUsage() throws Exception {

        ElectricityReading electricityReading = new ElectricityReading(Instant.now().minusSeconds(3600), BigDecimal.valueOf(25.0));
        ElectricityReading otherReading = new ElectricityReading(Instant.now(), BigDecimal.valueOf(3.0));
        meterReadingService.storeReadings(SMART_METER_ID, Arrays.asList(electricityReading, otherReading));

        List<Map.Entry<String, BigDecimal>> expectedPricePlanToCost = new ArrayList<>();
        expectedPricePlanToCost.add(new AbstractMap.SimpleEntry<>(PRICE_PLAN_2_ID, BigDecimal.valueOf(14.0)));
        expectedPricePlanToCost.add(new AbstractMap.SimpleEntry<>(PRICE_PLAN_3_ID, BigDecimal.valueOf(28.0)));
        expectedPricePlanToCost.add(new AbstractMap.SimpleEntry<>(PRICE_PLAN_1_ID, BigDecimal.valueOf(140.0)));

        assertThat(controller.recommendCheapestPricePlans(SMART_METER_ID, 5).getBody()).isEqualTo(expectedPricePlanToCost);
    }

    @Test
    public void givenNoMatchingMeterIdShouldReturnNotFound() {
        assertThat(controller.calculatedCostForEachPricePlan("not-found").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void shouldCalculateCostForLastWeek()throws Exception{

        ElectricityReading electricityReadingFirst = new ElectricityReading(Instant.now().minusSeconds(604800), BigDecimal.valueOf(3.0));
        ElectricityReading electricityReadingSecond = new ElectricityReading(Instant.now().minusSeconds(518400), BigDecimal.valueOf(4.0));
        ElectricityReading otherReading = new ElectricityReading(Instant.now(), BigDecimal.valueOf(3.0));

        meterReadingService.storeReadings(SMART_METER_ID, Arrays.asList(electricityReadingFirst,electricityReadingSecond,otherReading));

        List<Map.Entry<String, BigDecimal>> expectedPriceLastWeekToCost = new ArrayList<>();

         expectedPriceLastWeekToCost.add(new AbstractMap.SimpleEntry<>(PRICE_PLAN_1_ID, BigDecimal.valueOf(1.0)));
        assertThat(controller.calculatedCostForLastWeek(SMART_METER_ID).getBody()).isEqualTo(expectedPriceLastWeekToCost);


    }
    @Test
    public void shouldReturnNonFoundWhenLastWeekUsageIsNull()throws Exception{

        ElectricityReading electricityReading = new ElectricityReading(Instant.now().minusSeconds(3600), BigDecimal.valueOf(0.2569));
        ElectricityReading otherReading = new ElectricityReading(Instant.now(), BigDecimal.valueOf(3.0));

        meterReadingService.storeReadings(SMART_METER_ID, Arrays.asList(electricityReading,otherReading));

        assertThat(controller.calculatedCostForLastWeek(SMART_METER_ID).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);


    }
    @Test
    public void ShouldReturnMessageWhenPricePlanIdIsNull() {

        assertThat(controller.calculatedCostForLastWeek("smart_meter_5").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);


    }
    @Test
    public void shouldCalulatedCostDayOfWeek() throws Exception {

        ElectricityReading electricityReading = new ElectricityReading(Instant.now().minusSeconds(1800), BigDecimal.valueOf(1.0));
        ElectricityReading otherReading = new ElectricityReading(Instant.now(), BigDecimal.valueOf(1.0));
        meterReadingService.storeReadings(SMART_METER_ID, Arrays.asList(electricityReading, otherReading));

        Map<String,Object> result = new HashMap<>();
        result.put("consumptions",BigDecimal.valueOf(20.0));
        result.put(PRICE_PLAN_ID_KEY,PRICE_PLAN_1_ID);
        result.put("day of week",Instant.now().atZone(ZoneId.systemDefault()).getDayOfWeek());

        assertThat(controller.calculatedCostDayOfWeek(SMART_METER_ID).getBody()).isEqualTo(result);
    }
    @Test
    public void calculatedCostForDaysOfWeek() throws Exception {

        ElectricityReading electricityReading = new ElectricityReading(Instant.now().minusSeconds(1800), BigDecimal.valueOf(1.0));
        ElectricityReading otherReading = new ElectricityReading(Instant.now(), BigDecimal.valueOf(1.0));
        meterReadingService.storeReadings(SMART_METER_ID, Arrays.asList(electricityReading, otherReading));

        List<Map.Entry<String, BigDecimal>> result = new ArrayList<>();
        LocalDate todayDate = LocalDate.now();
        result.add(new AbstractMap.SimpleEntry<>(String.valueOf(todayDate.getDayOfWeek()),BigDecimal.valueOf(20.0)));
        assertThat(controller.calculatedCostForDaysOfWeek(SMART_METER_ID).getBody()).isEqualTo(result);
    }

    @Test
    public void shouldRecommendCheapestPricePlansForDaysOfWeekNoLimitForMeterUsage() throws Exception {

        ElectricityReading electricityReading = new ElectricityReading(Instant.now().minusSeconds(1800), BigDecimal.valueOf(3.0));
        ElectricityReading otherReading = new ElectricityReading(Instant.now(), BigDecimal.valueOf(3));
        meterReadingService.storeReadings(SMART_METER_ID, Arrays.asList(electricityReading, otherReading));

        List<Map.Entry<String, Map<String,BigDecimal>>>expectedPricePlanToCost = new ArrayList<>();
        LocalDate todayDate = LocalDate.now();
        Map<String,BigDecimal> result = new HashMap<>();
        result.put(PRICE_PLAN_2_ID,BigDecimal.valueOf(6.0));
        result.put(PRICE_PLAN_3_ID,BigDecimal.valueOf(12.0));
        result.put(PRICE_PLAN_1_ID,BigDecimal.valueOf(60.0));
        expectedPricePlanToCost.add(new AbstractMap.SimpleEntry<>(String.valueOf(todayDate.getDayOfWeek()),result));
        assertThat(controller.recommendCheapestPricePlansForDaysOfWeek(SMART_METER_ID, null).getBody()).isEqualTo(expectedPricePlanToCost);
    }
    @Test
    public void shouldRecommendCheapestPricePlansForDaysOfWeekLimitForMeterUsage() throws Exception {

        ElectricityReading electricityReading = new ElectricityReading(Instant.now().minusSeconds(1800), BigDecimal.valueOf(35.0));
        ElectricityReading otherReading = new ElectricityReading(Instant.now(), BigDecimal.valueOf(3.0));
        meterReadingService.storeReadings(SMART_METER_ID, Arrays.asList(electricityReading, otherReading));

        List<Map.Entry<String, Map<String,BigDecimal>>>expectedPricePlanToCost = new ArrayList<>();
        LocalDate todayDate = LocalDate.now();
        Map<String,BigDecimal> result = new HashMap<>();
        result.put(PRICE_PLAN_2_ID,new BigDecimal(38.0));
        result.put(PRICE_PLAN_3_ID,new BigDecimal(76.0));
        result.put(PRICE_PLAN_1_ID,new BigDecimal(380.0));
        expectedPricePlanToCost.add(new AbstractMap.SimpleEntry<>(String.valueOf(todayDate.getDayOfWeek()),result));
        assertThat(controller.recommendCheapestPricePlansForDaysOfWeek(SMART_METER_ID, 1).getBody()).isEqualTo(expectedPricePlanToCost);
    }
    @Test
    public void shouldRecommendCheapestPricePlansForDaysOfWeekWhenLimitISZero() throws Exception {

        ElectricityReading electricityReading = new ElectricityReading(Instant.now().minusSeconds(1800), BigDecimal.valueOf(35.0));
        ElectricityReading otherReading = new ElectricityReading(Instant.now(), BigDecimal.valueOf(3.0));
        meterReadingService.storeReadings(SMART_METER_ID, Arrays.asList(electricityReading, otherReading));

        List<Map.Entry<String, Map<String,BigDecimal>>>expectedPricePlanToCost = new ArrayList<>();
        LocalDate todayDate = LocalDate.now();
        assertThat(controller.recommendCheapestPricePlansForDaysOfWeek(SMART_METER_ID, 0).getBody()).isEqualTo(expectedPricePlanToCost);
    }

}
