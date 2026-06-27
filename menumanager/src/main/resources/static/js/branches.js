/* ==========================================================================
   /manage/branches
   Inline "edit table count" modal handler. Re-renders the modal form body
   so it POSTs to /manage/branch/{id}/update-tables.
   ========================================================================== */
(function () {
    let currentBranchId = null;

    window.editTableCount = function (button) {
        currentBranchId = button.getAttribute('data-branch-id');
        const currentTables = parseInt(button.getAttribute('data-tables'), 10);
        document.getElementById('newTableCount').value = currentTables;

        const form = document.getElementById('tableCountForm');
        form.action = '/manage/branch/' + currentBranchId + '/update';
        form.innerHTML =
            '<input type="hidden" name="location" value="" id="locationField">' +
            '<input type="hidden" name="tableCount" id="tableCountField">' +
            '<div class="modal-body">' +
                '<div class="mb-3">' +
                    '<label for="newTableCount" class="form-label fw-bold">Number of tables</label>' +
                    '<input type="number" class="form-control" id="newTableCount" min="1" max="500" value="' + currentTables + '" required>' +
                    '<small class="form-text text-muted">Updating this changes how many table QR codes are generated.</small>' +
                '</div>' +
            '</div>' +
            '<div class="modal-footer">' +
                '<button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>' +
                '<button type="submit" class="btn btn-primary fw-bold">' +
                    '<i class="bi bi-check-lg me-1"></i>Save changes' +
                '</button>' +
            '</div>';

        new bootstrap.Modal(document.getElementById('editTableModal')).show();
    };

    const form = document.getElementById('tableCountForm');
    if (form) {
        form.addEventListener('submit', function (e) {
            e.preventDefault();
            const tableCount = document.getElementById('newTableCount').value;
            fetch('/manage/branch/' + currentBranchId + '/update-tables', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: 'tableCount=' + encodeURIComponent(tableCount)
            })
            .then(function (response) {
                if (response.ok) location.reload();
                else alert('Failed to update table count');
            })
            .catch(function () { alert('Error updating table count'); });
        });
    }
})();
