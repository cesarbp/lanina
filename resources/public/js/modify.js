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
function get_mayoreo_cantidad() {
    return parseInt(get_value('mayoreo_cantidad'));
}
function get_mayoreo_descuento() {
    return parseFloat(get_value('mayoreo_descuento'));
}
function get_mayoreo_precio() {
    return parseFloat(get_value('mayoreo_precio'));
}

function calc_gan() {
    var prev = get_precio_venta();
    var cu = get_costo_unitario();

    if ( prev && cu && cu !== 0 )
    {
        var gan = 100 * ((prev / cu) - 1);
        $('#gan').val(gan.toFixed(2).toString());
    }
}

function calc_prev() {
    var cu = get_costo_unitario();
    var gan = get_ganancia();

    if ( gan && cu )
    {
        var prev = gan * cu;
        $('#precio_venta').val(prev.toFixed(2).toString());
    }
}

function calc_cu() {
    var ccj = get_costo_caja();
    var pres = get_presentacion();
    if ( ccj && pres && pres !== 0 )
    {
        var cu = ccj / pres;
        $('#costo_unitario').val(cu.toFixed(2).toString());
        $('input[type=hidden][name=costo_unitario]').val(cu.toFixed(2).toString());
    }
}

function calc_pm() {
    console.log("pm");
    var dm = get_mayoreo_descuento();
    var prev = get_precio_venta();
    var pm = prev * (1.0 - (dm / 100.0));
    $('#mayoreo_precio').val(pm.toFixed(2).toString());
}

function calc_dm() {
    var pm = get_mayoreo_precio();
    var prev = get_precio_venta();
    if ( prev > 0 ) {
        var dm = (1 - (pm / prev)) * 100;
        $('#mayoreo_descuento').val(dm.toFixed(2).toString());
    }
}

function well_rounded(n) {
    return (n - 1) % 10 !== 0;
}

function prev_up() {
    var prev = get_precio_venta();
    var cu = get_costo_unitario();

    if ( prev && cu )
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
        calc_pm();
        calc_dm();
    }
    $('#precio_venta').focus();
}

function prev_down() {
    var prev = get_precio_venta();
    var cu = get_costo_unitario();

    if ( prev && cu )
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
        calc_pm();
        calc_dm();
    }
    $('#precio_venta').focus();
}

function mprev_up() {
    var mp = get_mayoreo_precio();
    if ( mp ) {
        var ceiled = Math.ceil(mp * 10);
        if (ceiled === mp * 10)
            ceiled = ceiled + 1;
        var correct;
        if ( well_rounded(ceiled) )
            correct = (ceiled / 10).toFixed(2);
        else
            correct = ((ceiled + 1) / 10).toFixed(2);

        $('#mayoreo_precio').val(correct.toString());

        calc_dm();
    }
}

function mprev_down() {
    var mp = get_mayoreo_precio();
    if ( mp ) {
        var floored = Math.floor(mp * 10);
        if (floored === mp * 10)
            floored = floored - 1;
        var correct;
        if ( well_rounded(floored) )
            correct = (floored / 10).toFixed(2);
        else
            correct = ((floored - 1) / 10).toFixed(2);

        $('#mayoreo_precio').val(correct.toString());

        calc_dm();
    }
}


$(document).ready(function () {
    $('#precio_venta').keyup(function (e) {
        var kc = e.keyCode || e.which;
        if ( kc == 38 )
            prev_up();
        else if ( kc == 40 )
            prev_down();
        return false;
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
        calc_pm();
        calc_dm();
    });
    $('#precio_venta').blur(function() {
        calc_gan();
        calc_pm();
        calc_dm();
    });
    $('#mayoreo_descuento').blur(function () {
        calc_pm();
    });
    $('#mayoreo_precio').blur(function() {
        calc_dm();
    });
    $('#precio_venta').on('select focus', function() {
        $('#precio_venta')[0].setSelectionRange(0,0);
    });
    $('#the-form').submit(function () {
        var ans = confirm("Desea grabar el articulo? OK/ACEPTAR=GRABAR, CANCELAR=REGRESAR a hacer correcciones");
        if ( !ans )
        {
            $('form:first *:input[type!=hidden]:first')[0].setSelectionRange(0,0);
            $('form:first *:input[type!=hidden]:first').focus();
            return false;
        }
        return true;
    });
});
