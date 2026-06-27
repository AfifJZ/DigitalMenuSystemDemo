/* ==========================================================================
   /billing
   Refund-modal helper, plus auto-switch to the "history" tab when the
   URL hash says so.
   ========================================================================== */
function openRefundModal(orderId) {
    document.getElementById('refundOrderIdDisplay').innerText = orderId;
    document.getElementById('refundOrderIdInput').value = orderId;
    new bootstrap.Modal(document.getElementById('refundModal')).show();
}

window.openRefundModal = openRefundModal;

(function () {
    if (window.location.hash === '#history') {
        const tabEl = document.querySelector('#history-tab');
        bootstrap.Tab.getInstance(tabEl) || new bootstrap.Tab(tabEl).show();
    }
})();
