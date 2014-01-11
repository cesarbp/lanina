// for number-only inputs
$(document).ready(function() {
    $(".only-numbers").keydown(function (event) {
        var kc = event.which ? event.which : event.keyCode;
        if ( $.inArray(kc, [46,8,9,27,13,190,189]) !== -1 ||
             (event.keyCode == 65 && event.ctrlKey === true) ||
             (event.keyCode >= 35 && event.keyCode <= 39) ) {
            return;
        }
        else {
            if (event.shiftKey || (event.keyCode < 48 || event.keyCode > 57) && (event.keyCode < 96 || event.keyCode > 105 )) {
                event.preventDefault();
            }
        }
    });
});
