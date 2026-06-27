/* ==========================================================================
   /manage
   Chart.js initialisation for the dashboard. The three datasets
   (revenue, best categories, peak hours) are read from a JSON literal
   embedded in the page by Thymeleaf.

   The template renders:
     <script id="dashboard-data" type="application/json">
       { "chartLabels": [...], "chartData": [...],
         "bestCategoryLabels": [...], "bestCategoryData": [...],
         "peakHourLabels": [...], "peakHourData": [...] }
     </script>
   ========================================================================== */
(function () {
    if (typeof Chart === 'undefined') return;

    const dataEl = document.getElementById('dashboard-data');
    const data = dataEl ? JSON.parse(dataEl.textContent) : {};

    // --- Revenue chart -------------------------------------------------------
    const revenueCanvas = document.getElementById('revenueChart');
    if (revenueCanvas) {
        if (!data.chartLabels || data.chartLabels.length === 0) {
            revenueCanvas.outerHTML =
                '<div class="text-center text-muted py-5">'
                + '<i class="bi bi-graph-down fs-1"></i>'
                + '<p class="mt-3">No revenue data available yet.</p></div>';
        } else {
            new Chart(revenueCanvas.getContext('2d'), {
                type: 'bar',
                data: {
                    labels: data.chartLabels,
                    datasets: [{
                        label: 'Daily Revenue (RM)',
                        data: data.chartData,
                        backgroundColor: 'rgba(54, 162, 235, 0.6)',
                        borderColor: 'rgba(54, 162, 235, 1)',
                        borderWidth: 1,
                        borderRadius: 4
                    }]
                },
                options: {
                    responsive: true,
                    plugins: {
                        legend: { display: false },
                        tooltip: {
                            callbacks: {
                                label: function (ctx) { return 'RM ' + ctx.parsed.y.toFixed(2); }
                            }
                        }
                    },
                    scales: {
                        y: {
                            beginAtZero: true,
                            ticks: {
                                callback: function (value) { return 'RM ' + value.toFixed(2); }
                            }
                        }
                    }
                }
            });
        }
    }

    // --- Best-selling categories --------------------------------------------
    const bestCanvas = document.getElementById('bestCategoryChart');
    if (bestCanvas) {
        if (!data.bestCategoryLabels || data.bestCategoryLabels.length === 0) {
            bestCanvas.outerHTML =
                '<div class="text-center text-muted py-5">'
                + '<i class="bi bi-bag-x fs-1"></i>'
                + '<p class="mt-3">No sales data yet.</p></div>';
        } else {
            new Chart(bestCanvas.getContext('2d'), {
                type: 'bar',
                data: {
                    labels: data.bestCategoryLabels,
                    datasets: [{
                        label: 'Items sold',
                        data: data.bestCategoryData,
                        backgroundColor: 'rgba(255, 193, 7, 0.55)',
                        borderColor: 'rgba(255, 193, 7, 1)',
                        borderWidth: 1,
                        borderRadius: 4
                    }]
                },
                options: {
                    responsive: true,
                    plugins: { legend: { display: false } },
                    scales: { y: { beginAtZero: true } }
                }
            });
        }
    }

    // --- Peak hours --------------------------------------------------------
    const peakCanvas = document.getElementById('peakHourChart');
    if (peakCanvas) {
        if (!data.peakHourLabels || data.peakHourLabels.length === 0) {
            peakCanvas.outerHTML =
                '<div class="text-center text-muted py-5">'
                + '<i class="bi bi-clock-history fs-1"></i>'
                + '<p class="mt-3">No orders yet.</p></div>';
        } else {
            new Chart(peakCanvas.getContext('2d'), {
                type: 'line',
                data: {
                    labels: data.peakHourLabels,
                    datasets: [{
                        label: 'Orders',
                        data: data.peakHourData,
                        borderColor: 'rgba(13, 110, 253, 1)',
                        backgroundColor: 'rgba(13, 110, 253, 0.12)',
                        fill: true,
                        tension: 0.25,
                        pointRadius: 2
                    }]
                },
                options: {
                    responsive: true,
                    plugins: { legend: { display: false } },
                    scales: { y: { beginAtZero: true } }
                }
            });
        }
    }
})();
