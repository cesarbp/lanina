// Requires jquery, jquery-ui and trie.js

var res_url = "/json/article/starts_with";

var json = function (first_letter) {
    var res = [];
    $.getJSON(res_url, {'letter': first_letter}, function(results) {
	$.each(results, function(k, v) {
	    res.push(v["nom_art"]);
	});
    });
    return res;
}

$(document).ready(function(){
    var trie = {};
    var search_box_id = "#search";
    var jn;
    
    $(search_box_id).on("keyup change", function () {
	var inp = $(search_box_id).val();

	if (inp.length === 1) {
	    jn = json(inp);
	    $(search_box_id).autocomplete({source: jn});
	} else if (inp.length > 1) {
	    if (jn != null)
		$(search_box_id).autocomplete({source: jn});
	}
    });

});
