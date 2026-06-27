/* ==========================================================================
   /manage/branch/{id}/menu-import
   Select-all checkbox for the items table, and empty-selection guard.
   ========================================================================== */
(function () {
    const selectAll = document.getElementById('selectAll');
    if (selectAll) {
        selectAll.addEventListener('change', function () {
            document.querySelectorAll('.item-check').forEach(function (cb) {
                cb.checked = selectAll.checked;
            });
        });
    }

    const importForm = document.getElementById('importForm');
    if (importForm) {
        importForm.addEventListener('submit', function (e) {
            const mode = e.submitter && e.submitter.value;
            if (mode === 'selected') {
                const any = document.querySelectorAll('.item-check:checked').length > 0;
                if (!any) {
                    e.preventDefault();
                    alert('Select at least one item to import.');
                }
            }
        });
    }
})();
