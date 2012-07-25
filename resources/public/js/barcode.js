function toArray(obj) {
    var array = [];
    // iterate backwards ensuring that length is an UInt32
    for (var i = obj.length >>> 0; i--;) { 
	array[i] = obj[i];
    }
    return array;
}

function isInt(s) {
    for (var i = 0; i < s.length; i++) {
	if (!parseInt(s[i]))
	    return false;
    }
    return true;
}

function article_row(article) {
    var barcode = article.codigo;
    var name = article.nom_art;
    if (article.prev_con !== 0) {
	var price = article.prev_con;
    }
    else {
	var price = article.prev_sin;
    }

    var html = '<tr class="article-row" id=' + barcode.toString() + '><td class="art_barcode">' +
	barcode.toString() + '</td><td class="art_name">' +
	name.toString() + '</td><td class="art_price">' +
	price.toFixed(2).toString() + '</td><td class="art_quantity">' +
	'1' + '</td><td class="art_total">' +
	price.toFixed(2).toString() + '</td></tr>';
    return html;
}

function calculate_total() {
    var articles = $("#articles-table").children().children().slice(1);
    articles = toArray(articles);
    if (articles.length === 0) {
	return 0;
    }
    var totals = articles.map(function (r) {
	return parseFloat(r.children[4].innerHTML);
    });

    var total = totals.reduce(function (a,b) {return a + b;});
    return total;
}

function update_total() {
    setTimeout(function() {
	var total = calculate_total().toFixed(2);
	$("#total").text('Total: ' + total.toString());
    }, 200);    
}

function add_article_row(barcode) {
    if (isInt(barcode)) {
	var id_barcode = "#" + barcode;
	if ($(id_barcode).length !== 0) {
	    var quantity = $(id_barcode).children()[3].innerHTML;
	    var price = $(id_barcode).children()[2].innerHTML;
	    var new_quantity = 1 + parseInt(quantity);
	    var total = parseFloat(price) * new_quantity;
	    $(id_barcode).children()[3].innerHTML = new_quantity.toString();
	    $(id_barcode).children()[4].innerHTML = total.toFixed(2).toString();
	}
	else {
	    $.getJSON('/json/article', {'barcode': barcode}, function(article) {
		if (!$.isEmptyObject(article)) {
		    $("#articles-table").append(article_row(article));
		}
	    });
	}
	$("#barcode-field").val("");
	update_total();
    }
}

function barcode_listener (field, e) {
    var code = e.keyCode || e.which;
    if (code == 13) {
	var barcode = $("#barcode-field").val();
	add_article_row(barcode);
	return false;
    } else {
	return true;
    }
}
