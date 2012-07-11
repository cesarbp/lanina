// Every element in the array must start with the same char

function to_trie (arr) {
    var first = arr[0][0];
    var trie = {};
    trie[first] = {};

    for (var i = 0; i < arr.length; i++) {
	var word = arr[i];
	var letters = word.split("").splice(1);

	var current_pos = trie[first];
	for (var j = 0; j < letters.length; j++) {
	    var new_pos = current_pos[letters[j]];

	    if (new_pos == null) {
		current_pos[letters[j]] = {};
	    }
	    current_pos = current_pos[letters[j]];
	}
    }

    return trie;
}

function is_empty (a) {
    for (var b in a)
	return !1;
    return !0;
}

function branches (trie) {
    var branches = [];

    function _branches (trie, acc) {
	if (is_empty(trie)) {
	    branches.push(acc);
	}
	
	for (c in trie) {
	    _branches(trie[c], acc + c);
	}	
    }
    _branches(trie, "");
    return branches;
}

function walk_trie (trie, starting_str) {
    var current_pos = trie;
    var letters = starting_str.split("");
    
    for (var i = 0; i < letters.length; i++) {
	current_pos = current_pos[letters[i]];
    }

    var bs = branches(current_pos);

    bs = bs.map(function (w) {
	return starting_str + w;
    });

    return bs;
}
