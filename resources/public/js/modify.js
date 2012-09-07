function get_iva() {
    return $('select[name="iva"]').val();
}

function clear_from_iva() {
    var iva = get_iva();
    if (iva === "0") {
	$('#ccj_con').val("0.0");
	$('#cu_con').val("0.0");
	$('#prev_con').val("0.0");
    }
    else {
	$('#ccj_sin').val("0.0");
	$('#cu_sin').val("0.0");
	$('#prev_sin').val("0.0");
    }
}

function calculate_unitary() {
    var iva = get_iva();
    var pres = parseInt($('#pres').val());
    
    if (iva === "0") {
	var ccj = parseFloat($('#ccj_sin').val());
	var extra = ccj * 1.16;
    } else {
	var ccj = parseFloat($('#ccj_con').val());
	var extra = ccj / 1.16;
    }

    console.log(pres);
    console.log(ccj);

    if (pres > 0 && ccj > 0.0) {
	if (iva === "0") {
	    $('#cu_sin').val((ccj / pres).toFixed(2).toString());
	    $('#cu_con').val((extra / pres).toFixed(2).toString());
	    $('#ccj_con').val(extra.toFixed(2).toString());
	} else {
	    $('#cu_con').val((ccj / pres).toFixed(2).toString());
	    $('#cu_sin').val((extra / pres).toFixed(2).toString());
	    $('#ccj_sin').val(extra.toFixed(2).toString());
	}
    }
}

function check_utility() {
    var id = '#gan-control';
    var iva = parseInt(get_iva());
    var gan = parseFloat($('#gan').val());
    var correct;

    if (!gan || gan <= iva) {
	$(id).addClass('error');
	$('input[name="submit"]').attr('disabled', true);
	correct = false;
    } else {
	$(id).removeClass('error');
	$('input[name="submit"]').attr('disabled', false);
	correct = true;
    }
    return correct;
}

function calc_prev() {
    var iva = get_iva();
    var gan = (parseFloat($("#gan").val()) + 100) / 100;
    if (iva === "0") {
	var cu = parseFloat($("#cu_sin").val());
	var extra = cu * 1.16;
    }
    else {
	var cu = parseFloat($("#cu_con").val());
	var extra = cu / 1.16;
    }
    if (cu && cu > 0) {
	if (iva === "0") {
	    $('#prev_sin').val((cu * gan).toFixed(2).toString());
	    $('#prev_con').val((extra * gan).toFixed(2).toString());
	} else {
	    $('#prev_con').val((cu * gan).toFixed(2).toString());
	    $('#prev_sin').val((extra * gan).toFixed(2).toString());
	}
    }
}

function well_rounded(n) {
    return (n - 1) % 10 !== 0;
}

function prev_up() {
    var iva = get_iva();
    if (iva === "0") {
	var prev = parseFloat($('#prev_sin').val());
	var cu = parseFloat($('#cu_sin').val());
    } else {
	var prev = parseFloat($('#prev_con').val());
	var cu = parseFloat($('#cu_con').val());
    }

    var ceiled = Math.ceil(prev * 10);
    if (ceiled === prev * 10){
	ceiled = ceiled + 1;
    }
    if (well_rounded(ceiled)) {
	var correct = (ceiled / 10).toFixed(2);
    }
    else {
	var correct = ((ceiled + 1) / 10).toFixed(2);
    }

    if (cu !== 0) {
	var new_util = 100 * (correct / cu - 1.0);
    }
    if (iva === "0") {
	$('#prev_sin').val(correct.toString());
    }
    else {
	$('#prev_con').val(correct.toString());
    }
    $('#gan').val(new_util.toFixed(2).toString());
    calc_prev();
    calculate_unitary();
}

function prev_down() {
    var iva = get_iva();
    if (iva === "0") {
	var prev = parseFloat($('#prev_sin').val());
	var cu = parseFloat($('#cu_sin').val());
    } else {
	var prev = parseFloat($('#prev_con').val());
	var cu = parseFloat($('#cu_con').val());
    }

    var floored = Math.floor(prev * 10);
    if (floored === prev * 10) {
	floored = floored - 1;
    }
    if (well_rounded(floored)) {
	var correct = (floored / 10).toFixed(2);
    }
    else {
	var correct = ((floored - 1) / 10).toFixed(2);
    }

    if (cu !== 0) {
	var new_util = 100 * (correct / cu - 1.0);
    }
    if (iva === "0") {
	$('#prev_sin').val(correct.toString());
    }
    else {
	$('#prev_con').val(correct.toString());
    }
    $('#gan').val(new_util.toFixed(2).toString());
    calc_prev();
    calculate_unitary();
}

function calc_util(){
    var iva = get_iva();
    if (iva === "0") {
	var new_util = 100 * (parseFloat($('#prev_sin').val()) / parseFloat($('#cu_sin').val()) - 1);
    } else {
	var new_util = 100 * (parseFloat($('#prev_con').val()) / parseFloat($('#cu_con').val()) - 1);
    }
    $('#gan').val(new_util.toFixed(2).toString());
    calc_prev();
    calculate_unitary();    
}

$(document).ready(function () {
    $('select[name="iva"]').change(function() {
	clear_from_iva();
    });
    $('#ccj_con').blur(function() {
	calculate_unitary();
	if ($('#gan').val().length > 0)
	    calc_prev();
    });
    $('#ccj_sin').blur(function() {
	calculate_unitary();
	if ($('#gan').val().length > 0)
	    calc_prev();
    });
    $('#gan').blur(function() {
	calc_prev();
    });
    $('#prev_con').blur(function() {
	calc_util();
    });
    $('#prev_sin').blur(function() {
	calc_util();
    });
});
