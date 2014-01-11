// Requires jquery, jquery-ui and trie.js
var jn;
var res_url = "/json/article/starts_with";
var prov_url = "/json/all-providers";

var json = function (first_letter) {
    var res = [];
    $.getJSON(res_url, {'letter': first_letter}, function(results) {
        $.each(results, function(k, v) {
            res.push(v["nom_art"]);
        });
    });
    return res;
}

var json2 = function () {
    $.getJSON(prov_url, {}, function(results) {
        var providers = results;

        $('#provider-name').typeahead({
            source:results,
            updater: function(item) {
                this.$element[0].value = item;
                this.$element[0].form.submit();
                return item;
            }
        });

    });
};

$(document).ready(function(){
    if ( $('#provider-name').length > 0 ) {
        json2();
    }
    else {
        var search_box_id = "#search";
        $.getJSON('/json/all-articles', {}, function(results) {
            jn = results.map(function(o) {
                return o['nom_art'];
            });
            $(search_box_id).typeahead({
                source:jn,
                updater: function(item) {
                    this.$element[0].value = item;
                    this.$element[0].form.submit();
                    return item;
                }
            });
        });
        $('#search').keydown(function(event) {
            if ( event.ctrlKey || event.altKey )
                event.preventDefault();

        });

        $('#search').focus();

        $(search_box_id).keydown(function(event) {
            if ( event.ctrlKey || event.altKey )
                event.preventDefault();

        });
    }


});
