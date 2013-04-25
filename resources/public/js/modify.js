// Used to change prices

function get_value(n) {
    var s = 'name=' + '"' + n + '"';
    return $('select[' + s + ']').val() || $('input[' + s + ']').val();
}

function get_iva() {
    return parseFloat(get_value('iva'));
}

function get_costo_unitario() {
    return parseFloat(get_value('costo_unitario'));
}

function get_precio_venta() {
    return parseFloat(get_value('precio_venta'));
}

function get_costo_caja() {
    return parseFloat(get_value('costo_caja'));
}

function get_ganancia() {
    return (100.0 + parseFloat(get_value('gan'))) / 100.0;
}

function get_presentacion() {
    return parseInt(get_value('pres'));
}

function calc_gan() {
    var prev = get_precio_venta();
    var cu = get_costo_unitario();

    if ( prev !== NaN && cu !== NaN && cu !== 0 )
    {
        var gan = 100 * ((prev / cu) - 1);
        $('#gan').val(gan.toFixed(2).toString());
    }
}

function calc_prev() {
    var cu = get_costo_unitario();
    var gan = get_ganancia();

    if ( gan !== NaN && cu !== NaN )
    {
        var prev = gan * cu;
        $('#precio_venta').val(prev.toFixed(2).toString());
    }
}

function calc_cu() {
    var ccj = get_costo_caja();
    var pres = get_presentacion();
    if ( ccj !== NaN && pres !== NaN && pres !== 0 )
    {
        var cu = ccj / pres;
        $('#costo_unitario').val(cu.toFixed(2).toString());
    }
}

function well_rounded(n) {
    return (n - 1) % 10 !== 0;
}

function prev_up() {
    var prev = get_precio_venta();
    var cu = get_costo_unitario();

    if ( prev != NaN && cu != NaN)
    {
        var ceiled = Math.ceil(prev * 10);
        if (ceiled === prev * 10)
            ceiled = ceiled + 1;
        var correct;
        if ( well_rounded(ceiled) )
            correct = (ceiled / 10).toFixed(2);
        else
            correct = ((ceiled + 1) / 10).toFixed(2);

        $('#precio_venta').val(correct.toString());

        calc_gan();
    }

}

function prev_down() {
    var prev = get_precio_venta();
    var cu = get_costo_unitario();

    if ( prev !== NaN && cu !== NaN)
    {
        var floored = Math.floor(prev * 10);
        if (floored === prev * 10)
            floored = floored - 1;
        var correct;
        if ( well_rounded(floored) )
            correct = (floored / 10).toFixed(2);
        else
            correct = ((floored - 1) / 10).toFixed(2);

        $('#precio_venta').val(correct.toString());

        calc_gan();
    }
}


$(document).ready(function () {
    $('input').keyup(function(e){

        $('#' + this.id).val($('#' + this.id).val().toUpperCase());
    });

    $('#costo_caja').blur(function() {
        calc_cu();
        calc_prev();
    });
    $('#pres').blur(function() {
        calc_cu();
        calc_prev();
    });
    $('#gan').blur(function() {
        calc_prev();
    });
    $('#costo_unitario').blur(function() {
        calc_prev();
    });
    $('#precio_venta').blur(function() {
        calc_gan();
    });
});
