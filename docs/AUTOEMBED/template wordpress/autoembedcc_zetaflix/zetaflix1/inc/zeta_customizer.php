<?php
/*
* -------------------------------------------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* -------------------------------------------------------------------------------------
*
* @since 1.0.0
*
*/
if(!function_exists('header_output')){
	function header_output(){

        $fcolor = zeta_get_option('featucolor','#00be08');
        $mcolor = zeta_get_option('maincolor','#408BEA');
		$bcolor = zeta_get_option('bgcolor','#F5F7FA');
		$style  = zeta_get_option('style','default');
        $custom = zeta_get_option('css');

        // Funsion
        $fbgcolor = zeta_get_option('fbgcolor','#000');
        $facolor  = zeta_get_option('facolor','#ffffff');
        $fhcolor  = zeta_get_option('fhcolor','#408bea');
        $playsze  = zeta_get_option('playsize','regular');
		$maxwidth = zeta_get_option('max_width','1200');

		echo "\n<style type='text/css'>\n";
		// fonts
		//zeta_compose_css('body', 'font-family', '"'.zeta_get_option('font','Roboto').'", sans-serif');
	    //zeta_compose_css('body', 'background-color', $bcolor);
		// Set max widht
		//zeta_compose_css('header.main .hbox,#contenedor,footer.main .fbox','max-width',$maxwidth.'px');
		// color
		//zeta_compose_css('a,.home-blog-post .entry-date .date,.top-imdb-item:hover>.title a,.module .content .items .item .data h3 a:hover,.head-main-nav ul.main-header li:hover>a,.login_box .box a.register', 'color', $mcolor);
		//zeta_compose_css('.nav_items_module a.btn:hover,.pagination span.current,.w_item_b a:hover>.data .wextra b:before,.comment-respond h3:before,footer.main .fbox .fmenu ul li a:hover','color', $mcolor);
		//zeta_compose_css('header.main .hbox .search form button[type=submit]:hover,.loading,#seasons .se-c .se-a ul.episodios li .episodiotitle a:hover,.sgeneros a:hover,.page_user nav.user ul li a:hover','color',$mcolor);
		//zeta_compose_css('footer.main .fbox .fmenu ul li.current-menu-item a,.posts .meta .autor i,.pag_episodes .item a:hover,a.link_a:hover,ul.smenu li a:hover','color', $mcolor);
		//zeta_compose_css('header.responsive .nav a.active:before, header.responsive .search a.active:before,.ztuser a.clicklogin:hover,.menuresp .menu ul.resp li a:hover,.menuresp .menu ul.resp li ul.sub-menu li a:hover','color', $mcolor);
		//zeta_compose_css('.sl-wrapper a:before,table.account_links tbody td a:hover,.zt_mainmeta nav.genres ul li a:hover','color', $mcolor);
		//zeta_compose_css('.zt_mainmeta nav.genres ul li.current-cat a:before,.zetaflix_player .options ul li:hover span.title','color', $mcolor);
		//zeta_compose_css('.head-main-nav ul.main-header li ul.sub-menu li a:hover,form.form-resp-ab button[type=submit]:hover>span,.sidebar aside.widget ul li a:hover', 'color', $mcolor);
		//zeta_compose_css('header.top_imdb h1.top-imdb-h1 span,article.post .information .meta span.autor,.w_item_c a:hover>.rating i,span.comment-author-link,.pagination a:hover','color',$mcolor);
		//zeta_compose_css('.letter_home ul.glossary li a:hover, .letter_home ul.glossary li a.active, .user_control a.in-list', 'color', $mcolor);
        //zeta_compose_css('.headitems a#zetaflix_signout:hover, .login_box .box a#c_loginbox:hover','color',$mcolor);
		//zeta_compose_css('.report_modal .box .form form fieldset label:hover > span.title', 'color', $mcolor);

        // Background
		//zeta_compose_css('.linktabs ul li a.selected,ul.smenu li a.selected,a.liked,.module .content header span a.see-all,.page_user nav.user ul li a.selected,.zt_mainmeta nav.releases ul li a:hover','background', $mcolor);
		//zeta_compose_css('a.see_all,p.form-submit input[type=submit]:hover,.report-video-form fieldset input[type=submit],a.mtoc,.contact .wrapper fieldset input[type=submit],span.item_type,a.main', 'background', $mcolor);
		//zeta_compose_css('.post-comments .comment-reply-link:hover,#seasons .se-c .se-q span.se-o,#edit_link .box .form_edit .cerrar a:hover','background',$mcolor);
		//zeta_compose_css('.user_edit_control ul li a.selected,form.update_profile fieldset input[type=submit],.page_user .content .paged a.load_more:hover,#edit_link .box .form_edit fieldset input[type="submit"]','background', $mcolor);
		//zeta_compose_css('.login_box .box input[type="submit"],.form_post_lik .control .left a.add_row:hover,.form_post_lik .table table tbody tr td a.remove_row:hover,.form_post_lik .control .right input[type="submit"]','background', $mcolor);
		//zeta_compose_css('#zt_contenedor','background-color', $bcolor);
		//zeta_compose_css('.plyr input[type=range]::-ms-fill-lower', 'background', $mcolor);
		//zeta_compose_css('.menuresp .menu .user a.ctgs,.menuresp .menu .user .logout a:hover', 'background', $mcolor);
		//zeta_compose_css('.plyr input[type=range]:active::-webkit-slider-thumb', 'background', $mcolor);
		//zeta_compose_css('.plyr input[type=range]:active::-moz-range-thumb', 'background', $mcolor);
		//zeta_compose_css('.plyr input[type=range]:active::-ms-thumb', 'background', $mcolor);
		//zeta_compose_css('.tagcloud a:hover,ul.abc li a:hover,ul.abc li a.select, ','background',$mcolor);
        //zeta_compose_css('.featu','background',$fcolor);
		//zeta_compose_css('.report_modal .box .form form fieldset input[type=submit]','background-color',$mcolor);

		// border color
		//zeta_compose_css('.contact .wrapper fieldset input[type=text]:focus, .contact .wrapper fieldset textarea:focus,header.main .hbox .zt_user ul li ul li:hover > a,.login_box .box a.register','border-color',$mcolor);
		//zeta_compose_css('.module .content header h1','border-color', $mcolor);
		//zeta_compose_css('.module .content header h2','border-color', $mcolor);
		//zeta_compose_css('a.see_all','border-color', $mcolor);
		//zeta_compose_css('.top-imdb-list h3', 'border-color', $mcolor);
		//zeta_compose_css('.user_edit_control ul li a.selected:before','border-top-color', $mcolor);

        // Colors for styles
        switch($style) {
            case 'dark':
                //zeta_compose_css('header.main .loading', 'color', '#fff!important');
                //zeta_compose_css('.starstruck .star-on-png:before','color', $mcolor);
                break;

            case 'fusion':
               // zeta_compose_css('header.main .loading', 'color', '#fff!important');
                //zeta_compose_css('header.main, header.responsive','background', $fbgcolor);
                //zeta_compose_css('.head-main-nav ul.main-header li a, .ztuser a#zetaflix_signout, header.responsive .nav a:before, header.responsive .search a:before, .ztuser a.clicklogin','color',$facolor);
                //zeta_compose_css('.head-main-nav ul.main-header li:hover>a, .ztuser a#zetaflix_signout:hover, header.main .hbox .search form button[type=submit]:hover','color', $fhcolor);
                //zeta_compose_css('.ztuser a.clicklogin:hover, header.responsive .nav a.active:before, header.responsive .search a.active:before, .ztuser a.clicklogin:hover','color', $fhcolor);
                //zeta_compose_css('.head-main-nav ul.main-header li ul.sub-menu','background',$fbgcolor);
                //zeta_compose_css('.head-main-nav ul.main-header li ul.sub-menu li a','color',$facolor);
                //zeta_compose_css('.head-main-nav ul.main-header li ul.sub-menu li a:hover','color',$fhcolor);
                break;
        }

        if($style == 'dark' && $playsze == 'bigger'){
            //zeta_compose_css('.zetaflix_player','border-bottom','none');
        }

		// custom CSS
		if($custom) echo "\n$custom\n";
		echo "</style>\n";
	}
	add_action('wp_head','header_output');
}

// Generate CSS Line
function zeta_compose_css($class, $type, $val) {
	echo sprintf('%s{%s:%s;}', $class, $type, $val)."\n";
}
