package com.skyair.resolutionagent.data;

import com.skyair.resolutionagent.model.Booking;
import com.skyair.resolutionagent.model.Customer;
import com.skyair.resolutionagent.model.Flight;
import com.skyair.resolutionagent.model.FlightStatus;
import com.skyair.resolutionagent.model.LoyaltyTier;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CustomerDataStore {

    private final Map<String, Customer> customers = new LinkedHashMap<>();
    private final Map<String, Booking> bookings = new LinkedHashMap<>();

    public CustomerDataStore() {
        initializeData();
    }

    private void initializeData() {
        Customer priya = new Customer(
            "priya_nair",
            "Priya Nair",
            LoyaltyTier.GOLD,
            "SK4821X",
            "priya.nair@example.com",
            "+91-98xxxxxxx1",
            6,
            "1 prior complaint (delayed baggage, resolved with voucher)"
        );

        Flight priyaOutbound = new Flight(
            "SK-204",
            "DEL",
            "GOA",
            LocalDate.of(2026, 9, 23),
            LocalTime.of(18, 40),
            FlightStatus.CANCELLED,
            0,
            null
        );

        Flight priyaReturn = new Flight(
            "SK-205",
            "GOA",
            "DEL",
            LocalDate.of(2026, 9, 25),
            LocalTime.of(16, 20),
            FlightStatus.UNAFFECTED,
            0,
            null
        );

        Booking priyaBooking = new Booking(
            "SK4821X",
            "priya_nair",
            priyaOutbound,
            priyaReturn
        );

        Customer arvind = new Customer(
            "arvind_kulkarni",
            "Arvind Kulkarni",
            LoyaltyTier.SILVER,
            "TR1190B",
            "arvind.kulkarni@example.com",
            "+91-98xxxxxxx2",
            3,
            null
        );

        Flight arvindOutbound = new Flight(
            "SK-118",
            "BOM",
            "BLR",
            LocalDate.of(2026, 9, 23),
            LocalTime.of(7, 10),
            FlightStatus.DELAYED,
            240,
            LocalTime.of(11, 10)
        );

        Booking arvindBooking = new Booking(
            "TR1190B",
            "arvind_kulkarni",
            arvindOutbound,
            null
        );

        Customer meher = new Customer(
            "meher_kaur",
            "Meher Kaur",
            LoyaltyTier.PLATINUM,
            "WL7742",
            "meher.kaur@example.com",
            "+91-98xxxxxxx3",
            10,
            "1 prior complaint (overbooking, resolved with a tier-status upgrade)"
        );

        Flight meherOutbound = new Flight(
            "SK-305",
            "DEL",
            "HYD",
            LocalDate.of(2026, 9, 23),
            LocalTime.of(14, 0),
            FlightStatus.DELAYED,
            360,
            LocalTime.of(20, 0)
        );

        Booking meherBooking = new Booking(
            "WL7742",
            "meher_kaur",
            meherOutbound,
            null
        );

        customers.put(priya.id(), priya);
        bookings.put(priya.id(), priyaBooking);

        customers.put(arvind.id(), arvind);
        bookings.put(arvind.id(), arvindBooking);

        customers.put(meher.id(), meher);
        bookings.put(meher.id(), meherBooking);
    }

    public Optional<Customer> getCustomer(String customerId) {
        if (customerId == null) return Optional.empty();
        return Optional.ofNullable(customers.get(customerId));
    }

    public Optional<Booking> getBooking(String customerId) {
        if (customerId == null) return Optional.empty();
        return Optional.ofNullable(bookings.get(customerId));
    }

    public List<Customer> getAllCustomers() {
        return List.copyOf(customers.values());
    }

    public Map<String, Booking> getAllBookings() {
        return Map.copyOf(bookings);
    }
}
