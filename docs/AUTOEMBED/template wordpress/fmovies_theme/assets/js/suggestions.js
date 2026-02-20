


	var delay = (function(){
		var timer = 0;
		return function(callback, ms){
			clearTimeout (timer);
			timer = setTimeout(callback, ms);
		}
	})();
	var searchRequest = false,
		enterActive = true;
	jQuery('input[name="s"]').on("input", function() {
		var s = this.value;
		delay(function(){
		if( s.length <= 2 ) {
			jQuery(Suggestions.area).hide();
			jQuery(Suggestions.form).find('#search > i.fa').removeClass('hide').removeClass('');
			return;
		}
		if(!searchRequest) {
	    	searchRequest = true;
			jQuery(Suggestions.form).find('i').addClass('fa fa-refresh').addClass('');
			jQuery(Suggestions.area).find('ul').addClass('process').addClass('noselect');
			jQuery.ajax({
		      type:'GET',
		      url: Suggestions.api,
		      data: 'keyword=' + s + '&nonce=' + Suggestions.nonce,
		      dataType: "json",
		      success: function(data){
				if( data['error'] ) {
					jQuery(Suggestions.area).hide();
					return;
				}
				jQuery(Suggestions.area).show();
					var res = '<span class="icon-search-1">' + s + '</span>',
						moreReplace = Suggestions.more.replace('%s', res),
						moreText = '<li class="ctsx"><a class="more" href="javascript:;" onclick="document.getElementById(\'search\').submit();">' + moreReplace + '</a></li>';
						moreText2 = '';
					var items = [];
					jQuery.each( data, function( key, val ) {
					  	name = '';
					  	release_date = '';
					  	vote_average = '';
					  	poster = '';
					  	if( val['extra']['release_date'] !== false )
					    release_date = "<i class='dot'></i>" + val['extra']['release_date'];

					  	if( val['extra']['names'] !== false )
					  		name = val['extra']['names'];

					  	if( val['extra']['vote_average'] !== false )
					  		vote_average = "<div class='meta'><span class='imdb'><i class='fa fa-star'></i> " + val['extra']['vote_average'] + "</span>"+release_date+"</div>";

					   	items.push("<li id='" + key + "'><a href='" + val['url'] + "' class='item'><div class='poster'><img onerror='imgError(this);' src='" + val['poster'] + "' /></div><div class='info'><div class='title text-truncate'>" + val['title'] + "</div>" + vote_average + "</div></a></li>");
					});
					jQuery(Suggestions.area).html('<ul>' + items.join("") + moreText + '</ul>');
				},
				complete: function() {
			      	searchRequest = false;
			      	enterActive = false;
					jQuery(Suggestions.form).find('i').removeClass('hide').removeClass('');
					jQuery(Suggestions.area).find('ul').removeClass('process').removeClass('noselect');
				}
		   	});
		}	 
		}, 500 ); 
	});
	jQuery(document).on("keypress", "#search", function(event) { 
		if( enterActive ) {
			return event.keyCode != 13;
		}
	});
	jQuery(document).click(function() {
		var target = jQuery(event.target);
		if (jQuery(event.target).closest('input[name="s"]').length == 0) {
			jQuery(Suggestions.area).hide();
		} else {
			jQuery(Suggestions.area).show();
		}
	});
function imgError(image) {
    image.onerror = "https://image.tmdb.org/t/p/w370_and_h556_bestv2null";
	image.onerror = "";
	image.onerror = "undefined";
    image.src = "//i.imgur.com/z0iTYmn.png";
    return true;
}
