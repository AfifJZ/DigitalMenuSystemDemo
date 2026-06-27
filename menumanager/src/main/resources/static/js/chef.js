/* ==========================================================================
   /chef
   Auto-refreshing chef display page — polls the server every 15 seconds
   and replaces the orders container so the chef always sees the latest
   kitchen orders. No buttons, no actions — just display.
   ========================================================================== */
(function () {
    var ordersContainer = document.getElementById('ordersContainer');
    if (!ordersContainer) return;

    function refreshChefDisplay() {
        var url = window.location.pathname + window.location.search;
        fetch(url, { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
            .then(function (r) {
                if (!r.ok) throw new Error('Fetch failed');
                return r.text();
            })
            .then(function (html) {
                var parser = new DOMParser();
                var doc = parser.parseFromString(html, 'text/html');
                var newContainer = doc.getElementById('ordersContainer');
                if (newContainer) {
                    ordersContainer.innerHTML = newContainer.innerHTML;
                }
            })
            .catch(function () {
                // Silently retry on next interval
            });
    }

    setInterval(refreshChefDisplay, 15000);
})();