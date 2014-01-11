function showError(inputId, errorMsg) {
    if ( errorMsg && !$(inputId).parent().hasClass('error-bg') ) {
        $(inputId).after('<p class="error-msg">' + errorMsg + '</p>');
    }
    $(inputId).parent().addClass("error-bg");
    $(inputId).focus();
    $(inputId).select();
}

function removeError(inputId) {
    if ( $(inputId).parent().hasClass("error-bg") ) {
        $(inputId).parent().removeClass("error-bg");
        $(".error-msg").remove();
    }
}
