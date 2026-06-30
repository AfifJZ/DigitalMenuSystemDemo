// ==========================================================================
// /manage/branches
// Branch management functions
// ==========================================================================

let currentBranchId = null;
let pendingDeleteBranchName = null;

function editTableCount(button) {
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
}

function showDeleteBranchModal(button) {
    const branchId = button.getAttribute('data-branch-id');
    const branchName = button.getAttribute('data-branch-name');

    if (!branchId || !branchName) {
        alert('Could not read branch details from this button. Please refresh the page and try again.');
        return;
    }

    const nameDisplay = document.getElementById('deleteBranchNameDisplay');
    const nameConfirm = document.getElementById('branchNameConfirm');
    const passwordField = document.getElementById('branchPassword');
    const reportLink = document.getElementById('downloadReportLink');
    const deleteForm = document.getElementById('deleteBranchForm');
    const modalEl = document.getElementById('deleteBranchModal');

    if (!nameDisplay || !nameConfirm || !passwordField || !reportLink || !deleteForm || !modalEl) {
        alert('Delete modal is not properly configured. Please refresh the page.');
        return;
    }

    if (typeof bootstrap === 'undefined') {
        alert('Page scripts did not finish loading. Please refresh and try again.');
        return;
    }

    pendingDeleteBranchName = branchName;
    nameDisplay.textContent = branchName;
    nameConfirm.value = '';
    passwordField.value = '';
    reportLink.href = '/manage/export?branchId=' + encodeURIComponent(branchId);
    deleteForm.action = '/manage/branch/' + branchId + '/delete';

    bootstrap.Modal.getOrCreateInstance(modalEl).show();
}

function initBranchesPage() {
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

    const deleteForm = document.getElementById('deleteBranchForm');
    if (deleteForm) {
        deleteForm.addEventListener('submit', function (e) {
            const typedName = document.getElementById('branchNameConfirm').value.trim();
            if (!pendingDeleteBranchName || typedName !== pendingDeleteBranchName) {
                e.preventDefault();
                alert('Branch name does not match. Please type it exactly as shown.');
            }
        });
    }
}

window.editTableCount = editTableCount;
window.showDeleteBranchModal = showDeleteBranchModal;

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initBranchesPage);
} else {
    initBranchesPage();
}
