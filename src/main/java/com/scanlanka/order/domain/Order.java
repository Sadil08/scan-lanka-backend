package com.scanlanka.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Immutable financial record (09-orders). Lines are snapshotted in OrderItem. Money in LKR cents. */
@Entity
@Table(name = "\"order\"")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    @Column(name = "customer_id")
    private Long customerId;            // null = guest

    @Column(name = "guest_email")
    private String guestEmail;

    @Column(name = "contact_name", nullable = false)
    private String contactName;
    @Column(name = "contact_phone", nullable = false)
    private String contactPhone;
    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfilment_type", nullable = false)
    private FulfilmentType fulfilmentType;

    @Column(name = "ship_street") private String shipStreet;
    @Column(name = "ship_city") private String shipCity;
    @Column(name = "ship_province") private String shipProvince;
    @Column(name = "ship_postal_code") private String shipPostalCode;

    @Column(name = "bill_name") private String billName;
    @Column(name = "bill_tax_id") private String billTaxId;
    @Column(name = "bill_street") private String billStreet;
    @Column(name = "bill_city") private String billCity;
    @Column(name = "bill_province") private String billProvince;
    @Column(name = "bill_postal_code") private String billPostalCode;

    @Column(name = "subtotal_cents", nullable = false) private long subtotalCents;
    @Column(name = "discount_cents", nullable = false) private long discountCents;
    @Column(name = "delivery_cents", nullable = false) private long deliveryCents;
    @Column(name = "tax_cents", nullable = false) private long taxCents;
    @Column(name = "total_cents", nullable = false) private long totalCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_payment", nullable = false)
    private DeliveryPayment deliveryPayment = DeliveryPayment.PREPAID;
    @Column(name = "delivery_cod_cents", nullable = false) private long deliveryCodCents;

    /** Intended online instrument: CARD (PayHere) or BANK (slip). Null for COD. */
    @Column(name = "payment_method", length = 10)
    private String paymentMethod;

    @Column(name = "actual_delivery_cents") private Long actualDeliveryCents;
    @Column(name = "delivery_courier") private String deliveryCourier;

    @Column(name = "delivery_method") private String deliveryMethod;            // COMPANY_LORRY | COURIER (17)
    @Column(name = "courier_estimate_cents", nullable = false) private long courierEstimateCents; // display-only, COD
    @Column(name = "delivery_arranged", nullable = false) private boolean deliveryArranged;        // lorry far line(s) priced manually

    @Column(name = "carrier") private String carrier;
    @Column(name = "tracking_ref") private String trackingRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "paid_at") private Instant paidAt;
    @Column(name = "confirmed_at") private Instant confirmedAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {}

    public Order(String orderNumber, Long customerId, FulfilmentType fulfilmentType) {
        this.orderNumber = orderNumber;
        this.customerId = customerId;
        this.fulfilmentType = fulfilmentType;
    }

    public Long getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public Long getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public FulfilmentType getFulfilmentType() { return fulfilmentType; }
    public DeliveryPayment getDeliveryPayment() { return deliveryPayment; }
    public long getSubtotalCents() { return subtotalCents; }
    public long getDeliveryCents() { return deliveryCents; }
    public long getTaxCents() { return taxCents; }
    public long getTotalCents() { return totalCents; }
    public long getDeliveryCodCents() { return deliveryCodCents; }
    public Long getActualDeliveryCents() { return actualDeliveryCents; }
    public String getDeliveryCourier() { return deliveryCourier; }
    public String getCarrier() { return carrier; }
    public String getTrackingRef() { return trackingRef; }
    public Instant getCreatedAt() { return createdAt; }
    public String getShipStreet() { return shipStreet; }
    public String getShipCity() { return shipCity; }
    public String getShipProvince() { return shipProvince; }
    public String getShipPostalCode() { return shipPostalCode; }
    public String getBillName() { return billName; }
    public String getBillTaxId() { return billTaxId; }
    public String getBillStreet() { return billStreet; }
    public String getBillCity() { return billCity; }
    public String getBillProvince() { return billProvince; }
    public String getBillPostalCode() { return billPostalCode; }
    public String getContactPhone() { return contactPhone; }

    public void setGuestEmail(String v) { this.guestEmail = v; }
    public void setContact(String name, String phone, String email) {
        this.contactName = name; this.contactPhone = phone; this.contactEmail = email;
    }
    public void setShipAddress(String street, String city, String province, String postal) {
        this.shipStreet = street; this.shipCity = city; this.shipProvince = province; this.shipPostalCode = postal;
    }
    public void setBilling(String name, String taxId, String street, String city, String province, String postal) {
        this.billName = name; this.billTaxId = taxId; this.billStreet = street;
        this.billCity = city; this.billProvince = province; this.billPostalCode = postal;
    }
    public void setTotals(long subtotal, long discount, long delivery, long tax, long total) {
        this.subtotalCents = subtotal; this.discountCents = discount; this.deliveryCents = delivery;
        this.taxCents = tax; this.totalCents = total;
    }
    public void setDeliveryPayment(DeliveryPayment dp) { this.deliveryPayment = dp; }
    public void setDeliveryCodCents(long v) { this.deliveryCodCents = v; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(String deliveryMethod) { this.deliveryMethod = deliveryMethod; }
    public long getCourierEstimateCents() { return courierEstimateCents; }
    public void setCourierEstimateCents(long v) { this.courierEstimateCents = v; }
    public boolean isDeliveryArranged() { return deliveryArranged; }
    public void setDeliveryArranged(boolean v) { this.deliveryArranged = v; }
    public void setActualDelivery(Long cents, String courier) {
        this.actualDeliveryCents = cents;
        this.deliveryCourier = courier;
    }
    public void setPaidAt(Instant v) { this.paidAt = v; }
    public void setConfirmedAt(Instant v) { this.confirmedAt = v; }
}
