/* ==========================================================================
   /customer
   Cart, customization, payment, and live-queue logic for the customer
   menu page. Branch / table context is read from data-* attributes on
   <body>, which are populated by Thymeleaf.
   ========================================================================== */
(function () {
    const CUSTOMER_BRANCH_ID   = document.body.getAttribute('data-branch-id')   || null;
    const CUSTOMER_TABLE_NUMBER = document.body.getAttribute('data-table-number') || null;

    function orderContextQuery() {
        const params = new URLSearchParams();
        if (CUSTOMER_BRANCH_ID)   params.set('branchId',   CUSTOMER_BRANCH_ID);
        if (CUSTOMER_TABLE_NUMBER) params.set('tableNumber', CUSTOMER_TABLE_NUMBER);
        const qs = params.toString();
        return qs ? '?' + qs : '';
    }

    function appendOrderContext(url) {
        const ctx = orderContextQuery();
        if (!ctx) return url;
        return url + (url.indexOf('?') >= 0 ? '&' + ctx.substring(1) : ctx);
    }

    let cart = [];

    function addToCart(id, name, price) {
        const existing = cart.find(function (i) { return i.id === id; });
        if (existing) existing.quantity += 1;
        else cart.push({ id: id, name: name, price: price, quantity: 1, note: '' });
        updateCartUI();
        document.getElementById('toastMessage').innerText = name + ' added to cart!';
        new bootstrap.Toast(document.getElementById('cartToast')).show();
    }

    function updateCartUI() {
        let count = 0, total = 0.0, listHtml = '';

        cart.forEach(function (item, index) {
            count += item.quantity;
            total += item.price * item.quantity;

            listHtml += ''
                + '<li class="list-group-item bg-white p-3">'
                +   '<div class="d-flex justify-content-between align-items-center mb-2">'
                +     '<div>'
                +       '<h6 class="my-0 fw-bold text-dark">' + item.name + '</h6>'
                +       '<small class="text-success fw-bold">RM ' + (item.price * item.quantity).toFixed(2) + '</small>'
                +     '</div>'
                +     '<div class="d-flex align-items-center bg-light border rounded px-2 py-1">'
                +       '<button class="btn btn-sm text-primary fw-bold px-2 py-0 border-0" onclick="window.changeQuantity(' + index + ', -1)"><i class="bi bi-dash-lg"></i></button>'
                +       '<span class="mx-2 fw-bold text-dark" style="min-width: 20px; text-align: center;">' + item.quantity + '</span>'
                +       '<button class="btn btn-sm text-primary fw-bold px-2 py-0 border-0" onclick="window.changeQuantity(' + index + ', 1)"><i class="bi bi-plus-lg"></i></button>'
                +     '</div>'
                +   '</div>'
                +   '<div class="input-group input-group-sm mt-2">'
                +     '<span class="input-group-text bg-light text-muted border-end-0"><i class="bi bi-chat-text"></i></span>'
                +     '<input type="text" class="form-control border-start-0 bg-light"'
                +            ' placeholder="Add note (e.g., no onions, less ice)..."'
                +            ' value="' + item.note + '"'
                +            ' onkeyup="window.updateNote(' + index + ', this.value)">'
                +     '<button class="btn btn-outline-danger border-start-0" onclick="window.removeFromCart(' + index + ')" title="Remove Item"><i class="bi bi-trash"></i></button>'
                +   '</div>'
                + '</li>';
        });

        if (cart.length === 0) {
            listHtml = '<li class="list-group-item text-center text-muted py-5">'
                     + '<i class="bi bi-cart-x fs-1 d-block mb-3 text-secondary"></i>'
                     + 'Your cart is empty.<br><small>Looks like you have not made a choice yet!</small>'
                     + '</li>';
        }

        const cartCount  = document.getElementById('cartCount');     if (cartCount)  cartCount.innerText  = count;
        const cartMobile = document.getElementById('mobileCartCount');if (cartMobile) cartMobile.innerText = count;
        const itemsList  = document.getElementById('cartItemsList'); if (itemsList)  itemsList.innerHTML   = listHtml;
        const cartTotal  = document.getElementById('cartTotal');     if (cartTotal)  cartTotal.innerText  = total.toFixed(2);
    }

    function changeQuantity(index, delta) {
        cart[index].quantity += delta;
        if (cart[index].quantity <= 0) removeFromCart(index);
        else updateCartUI();
    }

    function updateNote(index, text) { cart[index].note = text; }

    function removeFromCart(index) {
        cart.splice(index, 1);
        updateCartUI();
    }

    function submitOrder() { processPayment('COUNTER'); }

    let currentItem = {};
    function openCustomizeModal(id, name, price) {
        currentItem = { id: id, name: name, price: price, quantity: 1, note: '' };
        const nameEl  = document.getElementById('customizeItemName');  if (nameEl)  nameEl.innerText  = name;
        const priceEl = document.getElementById('customizeItemPrice'); if (priceEl) priceEl.innerText = 'RM ' + price.toFixed(2);
        const qtyEl   = document.getElementById('customizeQuantity');  if (qtyEl)   qtyEl.innerText  = '1';
        const noteEl  = document.getElementById('customizeNote');      if (noteEl)  noteEl.value     = '';
        new bootstrap.Modal(document.getElementById('customizeModal')).show();
    }

    function adjustCustomizeQuantity(delta) {
        if (currentItem.quantity + delta > 0) {
            currentItem.quantity += delta;
            const qtyEl = document.getElementById('customizeQuantity');
            if (qtyEl) qtyEl.innerText = currentItem.quantity;
        }
    }

    function confirmAddToCart() {
        const noteEl = document.getElementById('customizeNote');
        if (noteEl) currentItem.note = noteEl.value;
        const existing = cart.find(function (i) {
            return i.id === currentItem.id && i.note === currentItem.note;
        });
        if (existing) existing.quantity += currentItem.quantity;
        else cart.push(Object.assign({}, currentItem));

        updateCartUI();
        const modal = bootstrap.Modal.getInstance(document.getElementById('customizeModal'));
        if (modal) modal.hide();
        document.getElementById('toastMessage').innerText = currentItem.name + ' added to cart!';
        new bootstrap.Toast(document.getElementById('cartToast')).show();
    }

    function showPaymentOptions() {
        if (cart.length === 0) return;
        const modal = bootstrap.Modal.getInstance(document.getElementById('cartModal'));
        if (modal) modal.hide();
        new bootstrap.Modal(document.getElementById('paymentModal')).show();
    }

    function getCartTotal() {
        return cart.reduce(function (sum, item) { return sum + item.price * item.quantity; }, 0);
    }

    function showToast(message, variant) {
        const toast = document.getElementById('cartToast');
        if (!toast) return;
        toast.classList.remove('text-bg-success', 'text-bg-warning', 'text-bg-danger');
        toast.classList.add('text-bg-' + (variant || 'success'));
        document.getElementById('toastMessage').innerText = message;
        new bootstrap.Toast(toast).show();
    }

    function processPayment(method) {
        if (cart.length === 0) return;

        if ((method === 'CARD' || method === 'ONLINE') && getCartTotal() < 2.00) {
            showToast('Card & FPX need at least RM 2.00 (Stripe rule). Use Pay at Counter for smaller orders.', 'warning');
            return;
        }

        if (method === 'COUNTER') {
            fetch(appendOrderContext('/api/order?paymentMethod=' + encodeURIComponent(method)), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(cart)
            })
            .then(function (r) { return r.ok ? r.json() : Promise.reject(); })
            .then(function (data) {
                if (data && data.orderId) localStorage.setItem('lastOrderId', String(data.orderId));
                cart = [];
                updateCartUI();
                const modal = bootstrap.Modal.getInstance(document.getElementById('paymentModal'));
                if (modal) modal.hide();
                new bootstrap.Modal(document.getElementById('successModal')).show();
            })
            .catch(function () {
                showToast('Error sending order. Please try again.', 'danger');
            });
            return;
        }

        fetch(appendOrderContext('/api/payments/stripe/checkout-session?paymentMethod=' + encodeURIComponent(method)), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(cart)
        })
        .then(async function (response) {
            const data = await response.json().catch(function () { return {}; });
            if (!response.ok) throw data;
            return data;
        })
        .then(function (data) {
            if (!data.checkoutUrl) throw new Error('Missing checkoutUrl');
            if (data && data.orderId) localStorage.setItem('lastOrderId', String(data.orderId));
            window.location.href = data.checkoutUrl;
        })
        .catch(function (err) {
            const msg = (err && err.error) ? err.error : 'Payment setup failed. Please try again or select Pay at Counter.';
            showToast(msg, 'warning');
        });
    }

    function setOrderModalState(hasOrder) {
        const details = document.getElementById('orderDetails');
        const empty   = document.getElementById('orderEmptyState');
        if (details) details.classList.toggle('d-none', !hasOrder);
        if (empty)   empty.classList.toggle('d-none',   hasOrder);
    }

    function loadLastOrder() {
        const urlParams = new URLSearchParams(window.location.search);
        const fromUrl = urlParams.get('orderId');
        const last    = fromUrl || localStorage.getItem('lastOrderId') || '';
        const input   = document.getElementById('viewOrderIdInput');
        if (input) input.value = last;
        if (last) loadOrder(last);
        else setOrderModalState(false);
    }

    function loadOrderById() {
        const input = document.getElementById('viewOrderIdInput');
        if (input && input.value) loadOrder(input.value);
    }

    function loadOrder(orderId) {
        fetch('/api/order/' + encodeURIComponent(orderId))
            .then(function (r) { return r.ok ? r.json() : Promise.reject(); })
            .then(function (data) {
                if (!data.ok) throw new Error(data.error || 'Order not found');
                localStorage.setItem('lastOrderId', String(data.id));
                setOrderModalState(true);

                const statusEl = document.getElementById('orderStatus');
                if (statusEl) {
                    statusEl.innerText = data.status || '';
                    statusEl.className = 'badge ' + (
                        data.status === 'KITCHEN'              ? 'bg-warning text-dark' :
                        data.status === 'COMPLETED'            ? 'bg-success' :
                        data.status === 'REFUND_REQUESTED'     ? 'bg-danger' :
                                                                'bg-secondary'
                    );
                }
                const timeEl = document.getElementById('orderTime');
                if (timeEl) timeEl.innerText = data.orderTime ? data.orderTime.replace('T', ' ') : '';
                const totalEl = document.getElementById('orderTotal');
                if (totalEl) {
                    const totalAmount = Number(data.totalAmount || 0);
                    totalEl.innerText = isFinite(totalAmount) ? totalAmount.toFixed(2) : '0.00';
                }

                const banner = document.getElementById('orderActionBanner');
                if (banner) {
                    banner.classList.add('d-none');
                    banner.className = 'alert d-none mb-3';
                    if (data.status === 'UPFRONT_PAYMENT') {
                        banner.className = 'alert alert-warning mb-3';
                        banner.innerHTML = '<i class="bi bi-exclamation-circle me-2"></i><span class="fw-bold">Please pay at the counter.</span> If payment is not made within 10 minutes, the order will be cancelled.';
                    } else if (data.status === 'CANCELLED') {
                        banner.className = 'alert alert-danger mb-3';
                        banner.innerHTML = '<i class="bi bi-x-octagon me-2"></i><span class="fw-bold">Order cancelled.</span> If you still want it, please place a new order.';
                    } else if (data.status === 'KITCHEN') {
                        banner.className = 'alert alert-info mb-3';
                        banner.innerHTML = '<i class="bi bi-fire me-2"></i><span class="fw-bold">Your order is in the kitchen queue.</span>';
                    } else if (data.status === 'REFUND_REQUESTED') {
                        banner.className = 'alert alert-warning mb-3';
                        banner.innerHTML = '<i class="bi bi-arrow-counterclockwise me-2"></i><span class="fw-bold">Refund requested.</span> Please wait for staff response.';
                    } else if (data.status === 'REFUNDED') {
                        banner.className = 'alert alert-success mb-3';
                        banner.innerHTML = '<i class="bi bi-check-circle me-2"></i><span class="fw-bold">Refunded.</span>';
                    }
                }

                const items = Array.isArray(data.items) ? data.items : [];
                const itemsList = document.getElementById('orderItemsList');
                if (itemsList) {
                    itemsList.innerHTML = items.map(function (it) {
                        return ''
                            + '<li class="list-group-item d-flex justify-content-between align-items-start">'
                            +   '<div>'
                            +     '<div class="fw-bold text-dark">' + (it.name || '') + '</div>'
                            +     (it.note ? '<div class="text-danger small mt-1"><i class="bi bi-chat-left-text"></i> Note: ' + it.note + '</div>' : '')
                            +   '</div>'
                            +   '<span class="badge bg-primary rounded-pill">x' + (it.quantity || 0) + '</span>'
                            + '</li>';
                    }).join('');
                }
            })
            .catch(function () {
                setOrderModalState(false);
                showToast('Order not found. Please check your order number.', 'warning');
            });
    }

    function requestRefund() {
        const input = document.getElementById('viewOrderIdInput');
        const orderId = input ? input.value : '';
        if (!orderId) return;
        const reasonEl = document.getElementById('refundReason');
        const reason = reasonEl ? (reasonEl.value || '') : '';

        fetch('/api/order/' + encodeURIComponent(orderId) + '/refund-request', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ reason: reason })
        })
        .then(function (r) { return r.ok ? r.json() : Promise.reject(); })
        .then(function (data) {
            if (!data.ok) throw new Error(data.error || 'Failed');
            showToast('Refund request sent to kitchen.', 'success');
            loadOrder(orderId);
        })
        .catch(function () {
            showToast('Failed to send refund request. Try again.', 'danger');
        });
    }

    function updateQueueCount() {
        fetch('/api/queue-count')
            .then(function (r) { return r.text(); })
            .then(function (count) {
                const a = document.getElementById('liveQueueCount');       if (a) a.innerText = count;
                const b = document.getElementById('liveQueueCountMobile'); if (b) b.innerText = count;
            })
            .catch(function () { /* swallow */ });
    }

    // Expose for inline onclick="..." attributes
    window.changeQuantity         = changeQuantity;
    window.updateNote             = updateNote;
    window.removeFromCart         = removeFromCart;
    window.submitOrder            = submitOrder;
    window.openCustomizeModal     = openCustomizeModal;
    window.adjustCustomizeQuantity = adjustCustomizeQuantity;
    window.confirmAddToCart       = confirmAddToCart;
    window.showPaymentOptions     = showPaymentOptions;
    window.processPayment         = processPayment;
    window.loadLastOrder          = loadLastOrder;
    window.loadOrderById          = loadOrderById;
    window.loadOrder              = loadOrder;
    window.requestRefund          = requestRefund;
    window.updateQueueCount       = updateQueueCount;

    // Boot
    updateCartUI();
    updateQueueCount();
    setInterval(updateQueueCount, 15000);
})();
