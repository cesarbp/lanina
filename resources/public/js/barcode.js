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
	var parsed = parseInt(s[i]);
	if (parsed !== 0 && !parsed)
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

    if (!barcode) {
	return false;
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

function ticket_links(article, quantity) {
    var ticket_link = $("#ticket");
    var bill_link = $("#bill");

    var req_html = article + "=" + quantity;
    var prev_html = article + "=" + (parseInt(quantity) - 1).toString();
    var ticket_href = '/tickets/nuevo/';

    if (ticket_link.length !== 0 && bill_link.length !== 0) {
	ticket_link_href = ticket_link[0].href;
	bill_link_href = bill_link[0].href;
	if (ticket_link_href.search("\\?" + prev_html) >= 0 || ticket_link_href.search("&" + prev_html) >= 0){
	    $("#ticket")[0].href = $("#ticket")[0].href.replace("?" + prev_html, "?" + req_html);
	    $("#ticket")[0].href = $("#ticket")[0].href.replace("&" + prev_html, "&" + req_html);
	    $("#bill")[0].href = $("#bill")[0].href.replace("?" + prev_html, "?" + req_html);
	    $("#bill")[0].href = $("#bill")[0].href.replace("&" + prev_html, "&" + req_html);	    
	} else {
	    $("#ticket")[0].href = ticket_link_href + "&" + req_html;
	    $("#bill")[0].href = bill_link_href + "&" + req_html;
	}
    } else {
	var ticket_html = '<a id="ticket" class="btn btn-primary" href="/tickets/nuevo/?' +
	    req_html + '">Generar Ticket</a>';
	var bill_html = '<a id="bill" class="btn btn-success" href="/facturas/nuevo/?' +
	    req_html + '">Generar Factura</a>';
	var form_html = '<div class="form-actions">' + ticket_html + bill_html + '</div>';
	$("#main").append(form_html);
    }
    return 0;
}

function add_article_row(barcode) {
    var worked = false;
    if (isInt(barcode)) {
	var id_barcode = "#" + barcode;
	if ($(id_barcode).length !== 0) {
	    var quantity = $(id_barcode).children()[3].innerHTML;
	    var price = $(id_barcode).children()[2].innerHTML;
	    var new_quantity = 1 + parseInt(quantity);
	    var total = parseFloat(price) * new_quantity;
	    $(id_barcode).children()[3].innerHTML = new_quantity.toString();
	    $(id_barcode).children()[4].innerHTML = total.toFixed(2).toString();
	    if ($(id_barcode).length !== 0)
		ticket_links(barcode, new_quantity.toString());
	}
	else {
	    $.getJSON('/json/article', {'barcode': barcode}, function(article) {
		if (article.codigo) {
		    $("#articles-table").append(article_row(article));
		    worked = true;
		}
	    });
	    setTimeout(function() {
		if (worked)
		    ticket_links(barcode, "1");
	    }, 200);	    
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

