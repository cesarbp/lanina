function article_row(article) {
    var barcode = article.codigo;
    var name = article.nom_art;
    if (article.prev_con !== 0) {
	var price = article.prev_con;
    }
    else {
	var price = article.prev_sin;
    }

    var html = '<tr class="article-row"><td class="art_barcode">' +
	barcode.toString() + '</td><td class="art_name">' +
	name.toString() + '</td><td class="art_price">' +
	price.toFixed(2).toString() + '</td></tr>';
    return html;
}

function add_article_row(barcode) {
    if (barcode.length == 13 || barcode.length == 8) {
	$.getJSON('/json/article', {'barcode': barcode}, function(article) {
	    $("#barcode-field").val(""); // Clear the input
	    if (article) {
		$(".articles-table").append(article_row(article));
	    }
	});
    }
}

$(document).ready(function(){
    $("#barcode-field").on("paste", function () {
	setTimeout(function () {
	    var barcode = $("#barcode-field").val();
	    add_article_row(barcode);
	}, 100);
    });
    $("#barcode-field").on("keyup change", function () {
	var barcode = $(this).val();
	add_article_row(barcode);
    });
});

		  
