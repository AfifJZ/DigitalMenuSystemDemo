/* ==========================================================================
   /kitchen
   Auto-refresh the active-orders view every 15 seconds so the queue
   stays in sync with new orders arriving via the customer page.
   ========================================================================== */
(function () {
    setTimeout(function () { location.reload(); }, 15000);
})();
