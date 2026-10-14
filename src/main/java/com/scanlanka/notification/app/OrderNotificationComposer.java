package com.scanlanka.notification.app;

import com.scanlanka.order.app.receipt.ReceiptModel;
import com.scanlanka.order.app.receipt.ReceiptService;
import com.scanlanka.order.domain.Order;
import com.scanlanka.order.domain.OrderItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/** Enqueues receipt + dispatch emails on order confirmation (10 + 06). */
@Service
public class OrderNotificationComposer {

    private final NotificationService notifications;
    private final ReceiptService receipts;
    private final EmailTemplateRenderer templates;
    private final String adminEmail;
    private final String orderCopyEmail;
    private final String frontendBaseUrl;

    public OrderNotificationComposer(NotificationService notifications, ReceiptService receipts,
                                       EmailTemplateRenderer templates,
                                       @Value("${app.notifications.admin-email}") String adminEmail,
                                       @Value("${app.notifications.order-copy-email:}") String orderCopyEmail,
                                       @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.notifications = notifications;
        this.receipts = receipts;
        this.templates = templates;
        this.adminEmail = adminEmail;
        this.orderCopyEmail = orderCopyEmail;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /**
     * Fired when a non-COD prepaid order is placed (bank transfer still PENDING_PAYMENT).
     * PayHere (CARD) placements skip this — see OrderPlacedNotifier. The buyer is emailed after
     * payment is confirmed ({@link #onOrderConfirmed}). COD is skipped here too.
     */
    public void onOrderPlaced(Order order, List<OrderItem> items) {
        ReceiptModel model = receipts.buildModel(order, items);
        EmailTemplateRenderer.RenderedEmail admin = templates.orderPlacedAdmin(model);
        enqueueToStaff("ADMIN_ORDER_PLACED", admin, "placed-admin:" + order.getId());
    }

    public void onOrderConfirmed(Order order, List<OrderItem> items) {
        ReceiptModel model = receipts.buildModel(order, items);
        receipts.ensurePdf(order, items);

        String lookupUrl = frontendBaseUrl + "/orders/lookup";
        EmailTemplateRenderer.RenderedEmail receipt = templates.orderReceipt(model, lookupUrl);
        notifications.enqueue("ORDER_RECEIPT", order.getContactEmail(), receipt.subject(), receipt.body(),
            "receipt:" + order.getId());

        EmailTemplateRenderer.RenderedEmail dispatch = templates.adminDispatch(model);
        enqueueToStaff("ADMIN_DISPATCH", dispatch, "dispatch:" + order.getId());
    }

    /** Order alerts go to the main admin mailbox and, if configured, a second staff mailbox. */
    private void enqueueToStaff(String type, EmailTemplateRenderer.RenderedEmail email, String key) {
        notifications.enqueue(type, adminEmail, email.subject(), email.body(), key);
        if (orderCopyEmail != null && !orderCopyEmail.isBlank()
            && !orderCopyEmail.trim().equalsIgnoreCase(adminEmail)) {
            notifications.enqueue(type, orderCopyEmail.trim(), email.subject(), email.body(), key + ":copy");
        }
    }

    public void resendReceipt(Order order, List<OrderItem> items) {
        ReceiptModel model = receipts.buildModel(order, items);
        receipts.ensurePdf(order, items);
        String lookupUrl = frontendBaseUrl + "/orders/lookup";
        EmailTemplateRenderer.RenderedEmail receipt = templates.orderReceipt(model, lookupUrl);
        notifications.enqueue("ORDER_RECEIPT", order.getContactEmail(), receipt.subject(), receipt.body(),
            "resend:" + order.getId() + ":" + System.nanoTime());
    }
}
