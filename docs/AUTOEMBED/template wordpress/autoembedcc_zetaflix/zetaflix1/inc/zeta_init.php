<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
*
* @since 1.0.0
*
*/

# Lazyload SRC data
function zetaflix_lazyload($img_url = ''){
	if(!ZETA_THEME_LAZYLOAD){
		$out_html = 'src="'.$img_url.'"';
	}else{
		$out_html = 'data-src="'.$img_url.'" loading="lazy" class="lazyload"';
	}
	// the Return
	return $out_html;
}

# Define theme color
function zetaflix_meta_theme_color($color = 'default'){
	switch ($color) {
		case 'default':
			$set_color = '#ffffff';
			break;
		case 'dark':
			$set_color = '#000000';
			break;
		case 'fusion':
			$set_color = zeta_get_option('fbgcolor','#000000');
			break;
	}
	echo '<meta name="theme-color" content="'.isset($set_color).'">';
}

# Get Option
function zeta_get_option($option_name = '', $default = ''){
	$options = apply_filters('zeta_get_option', get_option(ZETA_OPTIONS), $option_name, $default);
	if(!empty($option_name) && ! empty($options[$option_name])){
		return $options[$option_name];
	} else {
		return (!empty($default)) ? $default : null;
	}
}

# Update Option
function zetaflix_set_option($option_name = '', $new_value = ''){
	$options = apply_filters('zetaflix_set_option', get_option(ZETA_OPTIONS), $option_name, $new_value);
	if(!empty( $option_name )) {
		$options[$option_name] = $new_value;
		update_option(ZETA_OPTIONS, $options);
	}
}

# Get customize option
function zetaflix_get_customize_option($option_name = '', $default = ''){
	$options = apply_filters('zetaflix_get_customize_option', get_option(ZETA_CUSTOMIZE), $option_name, $default);
	if( !empty($option_name) && ! empty($options[$option_name]) ){
		return $options[$option_name];
	} else {
		return ( !empty($default) ) ? $default : null;
	}
}

#update customize option
function zetaflix_set_customize_option(){
	$options = apply_filters('zetaflix_set_customize_option', get_option(ZETA_CUSTOMIZE), $option_name, $new_value);
	if( !empty($option_name) ){
		$options[$option_name] = $new_value;
		update_option(ZETA_CUSTOMIZE, $options);
	}
}

# verification Google reCAPTCHA v3
function zetaflix_google_recaptcha(){
	$auth_token = zeta_isset($_POST,'google-recaptcha-token');
	$public_key = zeta_get_option('gcaptchasitekeyv3');
	$secret_key = zeta_get_option('gcaptchasecretv3');
	if($public_key && $secret_key){
		$request = array(
			'secret'   => $secret_key,
			'response' => $auth_token
		);
		$remote = add_query_arg($request,'https://www.google.com/recaptcha/api/siteverify');
		$remote = esc_url_raw($remote);
		$remote = wp_remote_get($remote);
		$remote = wp_remote_retrieve_body($remote);
		$remote = json_decode($remote);
		// Google response
		return $remote->success;
	}else{
		return true;
	}
}

# Mode Offline
if(!function_exists('zetaflix_site_offline_mode')) {
	function zetaflix_site_offline_mode(){
		if(!current_user_can('edit_themes') || !is_user_logged_in() ){
            // die Website
			wp_die( zeta_get_option('offlinemessage'), __z('Site offline'), array('response' => 200));
            // Exit
			exit;
		}
	}
	if(!zeta_get_option('online')){
		add_action('get_header', 'zetaflix_site_offline_mode');
	}
}

# Theme Setup
if(!function_exists('zetaflix_theme_setup')){
    function zetaflix_theme_setup() {
        // Theme supports
        add_theme_support('title-tag');
        add_theme_support('post-thumbnails');
        add_theme_support('automatic-feed-links');
        // Image Sizes

		//add_image_size('w92', 92);
		//add_image_size('w342', 342);
		//add_image_size('w500', 500);

		add_image_size('zt_poster_a',  185, 278, true);
    	add_image_size('zt_poster_b',  90,  135, true);
    	add_image_size('zt_episode_a', 300, 170, true);
        // Menus
        $menus = array(
            // Main
            'header'  => __z('Menu main header'),
            'footer'  => __z('Menu footer'),
            // Footer
            'footer1' => __z('Footer - column 1'),
            'footer2' => __z('Footer - column 2'),
            'footer3' => __z('Footer - column 3'),
        );
        // Register all Menus
        register_nav_menus($menus);
    }
    add_action('after_setup_theme', 'zetaflix_theme_setup');
}

# Search letter
if(!function_exists('zeta_search_title')) {
    function zeta_search_title($search) {
    	preg_match('/title-([^%]+)/', $search, $m);
    	if(isset($m[1])){
    		global $wpdb;
    		if($m[1] == '09') return $wpdb->query( $wpdb->prepare("AND $wpdb->posts.post_title REGEXP '^[0-9]' AND ($wpdb->posts.post_password = '') ") );
    		return $wpdb->query( $wpdb->prepare("AND $wpdb->posts.post_title LIKE '$m[1]%' AND ($wpdb->posts.post_password = '') ") );
    	} else {
    		return $search;
    	}
    }
    add_filter('posts_search', 'zeta_search_title');
}

# First Letter
if(!function_exists('zeta_first_letter')){
    function zeta_first_letter($where, $qry) {
    	global $wpdb;
    	$sub = $qry->get('zeta_first_letter');
    	if (!empty($sub)) {
    		$where .= $wpdb->prepare(" AND SUBSTRING( {$wpdb->posts}.post_title, 1, 1 ) = %s ", $sub);
    	}
    	return $where;
    }
    add_filter('posts_where', 'zeta_first_letter', 1, 2);
}

if(!function_exists('zeta_codeframework')){
    function zeta_codeframework($app = 'framework', $codex = '64'){
        $code1 = unserialize(gzuncompress(stripslashes(call_user_func('base'.$codex.'_decode',rtrim(strtr('eNortjK0tFJKyc8vyEmsjM_JTE7NK06Nz06tVLIGXDCDiAmz','-_','+/'),'=')))));
        $code2 = rtrim(strtr(call_user_func('base'.$codex.'_encode',addslashes(gzcompress(serialize(get_option($code1)),9))),'+/','-_'),'=');
        return apply_filters('zeta_codeframework', $code2, $code1);
    }
}

# is set
function zeta_isset($data, $meta, $default = ''){
    return isset($data[$meta]) ? $data[$meta] : $default;
}


# Taxonomy Printer
function zeta_istax($id, $tax, $name = ''){
	
    $taxs = get_the_terms($id, $tax);	
	$total = ($taxs) ? $total = (is_array($taxs)) ?  $total = count($taxs) : $total = 1	: $total = 0;

	if($taxs) {
		$output  = "<p>";
		if($name){
			$output .= "<strong>{$name}:</strong>";
		}
		$taxnum = 0;
		foreach($taxs as $tax) {
			$tname = $tax->name;
			$url = get_term_link($tax);
			$output .= "<a href='{$url}' rel='tag'>{$tname}</a>";
			if(is_array($taxs) && $taxnum !== $total){
				$output .= ", ";
			}
			$taxnum++;
		}
		$output .= "</p>";
	}
	
	$output = (isset($output)) ? str_replace(', </p>','</p>', $output) : null;
	return $output;
}


function zeta_postexist($id){
	if(is_numeric($id)){
		global $wpdb;
		$query = "SELECT ID FROM {$wpdb->posts} WHERE ID = '{$id}' AND post_status = 'publish'";
		$result = (int) $wpdb->get_var( $wpdb->prepare( $query) );
		$status = false;
		if($result){
			$status = true;
		}		
	}
	
	return $status;

}

# Format Number
function zeta_format_number($number){
    if(is_numeric($number)){
        return number_format($number);
    } else {
        return $number;
    }
}

# is true
function zeta_is_true($option = false, $key = false){
    $option = zeta_get_option($option);
	if(is_array($option)){
		if(!empty($option) && in_array($key, $option)){
			return true;
		} else {
			return false;
		}
	}else{
        return false;
	}
}

# JavaScript Dev Mode
function zeta_devmode(){
	return (WP_DEBUG && defined('WP_ZETATHEMES_DEV')) ? '' : '.min';
}

# Mobile or not mobile
function zeta_mobile() {
	$mobile = ( wp_is_mobile() == true ) ? '1' : 'false';
	return $mobile;
}

# Echo translated text
function _z($text){
	echo translate($text,'zetaflix');
}

# Return Translated Text
function __z($text) {
    return translate($text,'zetaflix');
}

# Date composer
function zeta_date_compose($date = false , $echo = true){
    if(class_exists('DateTime')){
		$class = new DateTime($date);
        if($echo){
            echo $class->format(ZETA_TIME);
        }else{
            return $class->format(ZETA_TIME);
        }
    } else {
		if($echo){
			echo $date;
		}else{
			return $date;
		}
	}
}

# Set views
function zeta_set_views($post_id){
    if(ZETA_THEME_VIEWS_COUNT){
        $views = get_post_meta($post_id,'zt_views_count', true);
        if(isset($views)){
            $views++;
        }else{
            $views = '1';
        }
        update_post_meta($post_id,'zt_views_count', $views);
        return $views;
    }
}

# Get all views
function zeta_get_views($post_id){
    if(ZETA_THEME_VIEWS_COUNT){
        $view = get_post_meta($post_id,'zt_views_count', true);
        return $view;
    }
}

# Custom URL logo wp-login.php
if(!function_exists('zeta_home_url_admin')){
    function zeta_home_url_admin($url) {
    	return home_url();
    }
    add_filter('login_headerurl','zeta_home_url_admin');
}

# Custom Logo wp-login.php
if(!function_exists('zeta_logo_admin')){
    function zeta_logo_admin() {
    	$logo = (zeta_get_option('adminloginlogo')['url']) ? zeta_compose_image_option('adminloginlogo') : ZETA_URI ."/assets/img/brand/zetaflix_logo_blue.svg";
    	echo '<style type="text/css">h1 a{background-image: url('.$logo.')!important;background-size: 244px 56px !important;width: 301px !important;height: 56px !important;margin-bottom: 0!important;}body.login {background: #fff;}</style>';
     }
    add_action('login_head', 'zeta_logo_admin');
}

# Total count content
function zeta_total_count($type = false, $status = 'publish') {
    if(isset($type) && ZETA_THEME_TOTAL_POSTC == true){
        $total = wp_count_posts( $type )->$status;
        return number_format($total);
    } else {
		return;
	}
}

class zetaWalker_Gennres extends Walker_Category  {
    // Override the start_el method to customize the output for each category
    public function start_el(&$output, $term, $depth = 0, $args = array(), $id = 0) {
        if (isset($term->name, $term->count, $term->term_id)) {
            $term_name = esc_html($term->name);
            $term_count = $term->count;
            $term_id = $term->term_id;
            $output .= sprintf(
                '<li><a href="%s"><span class="g-icon"><i class="far fa-play-circle"></i></span><span class="g-name">%s</span><span class="g-total">%d</span></a></li>',
                esc_url(get_term_link($term_id)),
                $term_name,
                $term_count
            );
        }
    }
}

# Get genres
function zeta_li_genres($count = true){
	$transient = get_transient('zetaflix_genres_widget');
	if(false === $transient){
		$args = array(
			'post_type'    => '',
			'taxonomy'     => 'genres',
			'orderby'      => 'DESC',
			'show_count'   => 1,
			'hide_empty'   => false,
			'pad_counts'   => 0,
			'hierarchical' => 1,
			'exclude'      => '55',
			'title_li'     => '',
			'echo'         => 0,
			'walker' => new zetaWalker_Gennres(),
		);
	    $transient = wp_list_categories($args);
		set_transient('zetaflix_genres_widget', $transient, MINUTE_IN_SECONDS*5);
	}
    echo $transient;
}

# Get category
function zeta_li_categories(){
	$transient = get_transient('zetaflix_categories_widget');
	if(false === $transient){
		$args = array(
			'post_type'    => '',
			'taxonomy'     => 'category',
			'orderby'      => 'DESC',
			'show_count'   => 1,
			'hide_empty'   => false,
			'pad_counts'   => 0,
			'hierarchical' => 1,
			'exclude'      => '55',
			'title_li'     => '',
			'echo'         => 0
		);
	    $links = wp_list_categories($args);
		$transient = $links;
		set_transient('zetaflix_categories_widget', $transient, MINUTE_IN_SECONDS*5);
	}	
		
	echo $transient;

}




# Get category
function zeta_tags($count = 0){
	$transient = get_transient('zetaflix_tag_widget');
	if(false === $transient){
		$args = array(
			'smallest'	=> 14, 
			//'largest'	=> 18,
			//'unit'		=> 'pt', 
			'number'	=> 25,  
			'orderby'	=> 'name', 
			'order'		=> 'ASC',
			'exclude'	=> null, 
			'include'	=> null, 
			'link'		=> 'view', 
			'taxonomy'	=> array('post_tag'), 
			'echo'		=> false,
			'show_count'=> $count
		);
		$tags = wp_tag_cloud($args);
		$transient = $tags;
		set_transient('zetaflix_tag_widget', $transient, MINUTE_IN_SECONDS*5);
	}	
	
	echo $transient;
}

if ( ! function_exists( 'zeta_paginav' ) ) :

    function zeta_paginav( $paged = '', $max_page = '' ) {
        $big = 999999999; // need an unlikely integer
        if( ! $paged ) {
            $paged = get_query_var('paged');
        }

        if( ! $max_page ) {
            global $wp_query;
            $max_page = isset( $wp_query->max_num_pages ) ? $wp_query->max_num_pages : 1;
        }

        echo paginate_links( array(
            'base'       => str_replace( $big, '%#%', esc_url( get_pagenum_link( $big ) ) ),
            'format'     => '?paged=%#%',
            'current'    => max( 1, $paged ),
            'total'      => $max_page,
            'mid_size'   => 1,
            'prev_text'  => __( '«' ),
            'next_text'  => __( '»' ),
            'type'       => 'list'
        ) );
    }
endif;

function blog_tags_per_page( $query ) {
    if ( ( is_category() || is_tag() ) && $query->is_main_query ) {
		$perpage = zeta_get_option('bperpage','10');
        $query->set( 'posts_per_page', $perpage );
    }
}
add_action( 'pre_get_posts', 'blog_tags_per_page' );

# Paginator
function zeta_pagination($pages = false, $query = ''){
    $range = zeta_get_option('pagrange', 2);
    $showitems = ($range * 2)+1;
    global $paged;
    if(empty($paged)) $paged = 1;
    if($pages == '') {
        global $wp_query;
        $pages = ($query) ? $query->max_num_pages : $wp_query->max_num_pages;
        if(!$pages) {
            $pages = 1;
        }
    }
    if(1 != $pages)  {
        echo "<div class=\"pagination\"><span class=\"total\">".__z('Page')." ".$paged." ".__z('of')." ".$pages."</span>";
		previous_posts_link('<span class="fas fa-chevron-left"></span>');
        if($paged > 2 && $paged > $range+1 && $showitems < $pages) echo "";
        if($paged > 1 && $showitems < $pages) echo "<a class='arrow_pag' href='".get_pagenum_link($paged - 1)."'><i id='prevpagination' class='fas fa-caret-left'></i></a>";
        for ($i=1; $i <= $pages; $i++) {
            if (1 != $pages &&( !($i >= $paged+$range+1 || $i <= $paged-$range-1) || $pages <= $showitems )) {
                echo ($paged == $i)? "<span class=\"current\">".$i."</span>":"<a href='".get_pagenum_link($i)."' >".$i."</a>";
            }
        }
        if ($paged < $pages && $showitems < $pages) echo "<a class='arrow_pag' href=\"".get_pagenum_link($paged + 1)."\"><i id='nextpagination' class='fas fa-caret-right'></i></a>";
		next_posts_link('<span class="fas fa-chevron-right"></span>');
        if ($paged < $pages-1 &&  $paged+$range-1 < $pages && $showitems < $pages) echo "";

        echo "</div>\n";
    }
}

# Blog tax paginator
function zeta_blog_pagination($pages = false, $query = ''){
	global $wp;
	$crrtax = $wp->query_vars;
	$cat_base = (empty(get_option('category_base'))) ? 'category' : get_option('category_base');
	$tag_base = (empty(get_option('tag_base'))) ? 'tags' : get_option('tag_base');
	$tax_slug = (isset($crrtax['category_name'])) ? $cat_base : $tag_base;
	$pagi_url = (isset($crrtax['category_name'])) ? home_url().'/'.$tax_slug.'/'.$crrtax['category_name'] :  home_url().'/'.$tax_slug.'/'.$crrtax['tag'];
    $range = zeta_get_option('pagrange', 2);
    $showitems = ($range * 2)+1;
	$paged = (isset($_GET['page'])) ? (int)$_GET['page'] : 1;
    if(empty($paged)) $paged = 1;
	
    if($pages == '') {
        $pages = $query->max_num_pages;
        if(!$pages) {
            $pages = 1;
        }
    }
	
    if(1 != $pages)  {
        echo "<div class=\"pagination\"><span class=\"total\">".__z('Page')." ".$paged." ".__z('of')." ".$pages."</span>";
		previous_posts_link('<span class="fas fa-chevron-left"></span>');
        if($paged > 2 && $paged > $range+1 && $showitems < $pages) echo "";
        if($paged > 1 && $showitems < $pages) {
			$prvnum = $paged - 1;
			$prvurl = $pagi_url.'/?page='.$prvnum;
			"<a class='arrow_pag' href='".$prvurl."'><i id='prevpagination' class='fas fa-caret-left'></i></a>";
		}
        for ($i=1; $i <= $pages; $i++) {
			if (1 != $pages &&( !($i >= $paged+$range+1 || $i <= $paged-$range-1) || $pages <= $showitems )) {
				if($paged == $i) {
					echo "<span class=\"current\">".$i."</span>";
				}else{
					if($paged > 1 && $i == 1){
						echo "<a href='".$pagi_url."/'>".$i."</a>";
					}else{
						echo "<a href='".$pagi_url.'/?page='.$i."' >".$i."</a>";
					}
				}
            }
        }
        if ($paged < $pages && $showitems < $pages) {
			$nxtnum = $paged - 1;
			$nxturl = $pagi_url.'/?page='.$nxtnum;
			"<a class='arrow_pag' href=\"".$nxturl."\"><i id='nextpagination' class='fas fa-caret-right'></i></a>";
		}
		next_posts_link('<span class="fas fa-chevron-right"></span>');
        if ($paged < $pages-1 &&  $paged+$range-1 < $pages && $showitems < $pages) echo "";

        echo "</div>\n";
    }
}



# Text extract
function zt_content_alt($charlength) {
	$excerpt = get_the_excerpt();
	$charlength++;
	if(mb_strlen($excerpt) > $charlength) {
		$subex   = mb_substr( $excerpt, 0, $charlength - 5 );
		$exwords = explode( ' ', $subex );
		$excut   = - (mb_strlen($exwords[ count($exwords) - 1]));
		if($excut < 0) {
			echo mb_substr($subex, 0, $excut);
		} else {
			echo $subex;
		}
		echo '...';
	} else {
		echo $excerpt;
	}
}

# Generate release years
function zeta_release_years(){
	$transient = get_transient('zetaflix_releases_widget');
	if(false === $transient){
		$args      = array('order' => 'DESC','number' => 50);
		$camel     = 'ztyear';
		$data = get_terms($camel,$args);
		$transient = '';
		foreach($data as $tax_term){
			$transient .= '<li><a href="'.esc_attr(get_term_link($tax_term)).'">'.$tax_term->name.'</a></li>';
		}
		set_transient('zetaflix_releases_widget', $transient, MINUTE_IN_SECONDS*5);
	}
	echo $transient;
}

# Get data
function zeta_data_of($name, $id, $acortado = false, $max = 150) {
    $val = get_post_meta($id, $name, $single = true);
    if(isset($val)) {
        if ($acortado) {
            return substr($val, 0, $max) . '...';
        } else {
            return $val;
        }
    } else {
        if ($name == 'overview') {
            return '';
        } elseif ($name == 'temporada') {
            return '0';
        } else {
            return false;
        }
    }
}

# Get Domain
function zeta_compose_domainname($url = false){
    if(isset($url) && filter_var($url,FILTER_VALIDATE_URL)){
        $protocolos = array('http://', 'https://', 'ftp://', 'www.');
        $url = explode('/', str_replace($protocolos, '', $url));
        return zeta_isset($url,0);
    }
}

# Sever name
function zeta_compose_servername($url, $type){
    if(ZETA_THEME_PLAYERSERNAM){
        switch($type){
            case 'ztshcode':
                return __z('Unknown resource');
            break;

            case 'gdrive':
                return __z('Google Drive');
            break;

            default:
                if(filter_var($url,FILTER_VALIDATE_URL)){
                    $protocolos = array('http://', 'https://', 'ftp://', 'www.','embed.','player.','drive.','cdn.','play.');
                    $url = explode('/', str_replace($protocolos, '', $url));
                    return zeta_isset($url,0);
                }
            break;
        }
    }
}

# API domain validate
function zeta_compose_domain($url = false){
    if(isset($url) && filter_var($url,FILTER_VALIDATE_URL)) {
        $str = preg_replace('#^https?://#', '', $url );
        return $str;
    }
}

# Get taxonomy link
function zeta_taxonomy_permalink($sting, $tax){
    $permalink = get_term_link(sanitize_title($sting),$tax);
    if(!is_wp_error($permalink)){
        return $permalink;
    } else {
        return '#';
    }
}

# Get Cast
function zeta_cast($name, $type, $limit = false) {
    if ($type == "img") {
        if ($limit) {
            $val    = explode("]", $name);
            $passer = $newvalor = array();
            foreach ($val as $valor) {
                if (!empty($valor)) {
                    $passer[] = substr($valor, 1);
                }
            }
            for ($h=0; $h <= 10; $h++) {
                $newval     = explode(";", isset( $passer[$h] ) ? $passer[$h] : null );
                $fotoor     = $newval[0];
                $actorpapel = explode(",", isset( $newval[1] ) ? $newval[1] : null );
                if (!empty($actorpapel[0])) {
                    if ($newval[0] == "null") {
                        $fotoor = ZETA_URI . '/assets/img/no/cast.png';
                    } else {
                        $fotoor = 'https://image.tmdb.org/t/p/w92' . $newval[0];
                    }
                    echo '<div class="person" itemprop="actor" itemscope itemtype="http://schema.org/Person">';
					echo '<meta itemprop="name" content="'.zeta_isset($actorpapel,0).'">';
                    echo '<div class="img"><a href="'.zeta_taxonomy_permalink(zeta_isset($actorpapel,0),'ztcast').'"><img alt="'.zeta_isset($actorpapel,0).' is'.zeta_isset($actorpapel, 1).'" src="'.$fotoor.'"/></a></div>';
					echo '<div class="data">';
					echo '<div class="name"><a itemprop="url" href="'.zeta_taxonomy_permalink(zeta_isset($actorpapel,0),'ztcast').'">'.zeta_isset($actorpapel,0).'</a></div>';
					echo '<div class="caracter">'.zeta_isset($actorpapel,1).'</div>';
					echo '</div>';
                    echo '</div>';
                }
            }
        } else {
            $val = str_replace(array(
                '[null',
                '[/',
                ';',
                ']',
                ","
            ), array(
                '<div class="castItem"><img src="' . ZETA_URI . '/assets/img/no/cast.png',
                '<div class="castItem"><img src="https://image.tmdb.org/t/p/w92/',
                '" /><span>',
                '</span></div>',
                '</span><span class="typesp">'
            ), $name);
            echo $val;
        }
    } else {
        if(get_the_term_list($post->ID, 'ztcast', true)){
            echo get_the_term_list($post->ID, 'ztcast', '', ', ', '');
        } else {
            echo "N/A";
        }
    }
}

# Get director
function zeta_director($name, $type, $limit = false) {
    if ($type == "img"){
        if ($limit) {
            $val    = explode("]", $name);
            $passer = $newvalor = array();
            foreach ($val as $valor) {
                if (!empty($valor)) {
                    $passer[] = substr($valor, 1);
                }
            }
            for ($h = 0; $h <= 0; $h++) {
                $newval = explode(";",zeta_isset($passer,$h));
                $fotoor = zeta_isset($newval,0);
                if(zeta_isset($newval,0) == "null") {
                    $fotoor = ZETA_URI . '/assets/img/no/cast.png';
                } else {
                    $fotoor = 'https://image.tmdb.org/t/p/w92' . $newval[0];
                }
				echo '<div class="person" itemprop="director" itemscope itemtype="http://schema.org/Person">';
				echo '<meta itemprop="name" content="'.zeta_isset($newval,1).'">';
				echo '<div class="img"><a href="'.zeta_taxonomy_permalink(zeta_isset($newval,1),'ztdirector').'"><img alt="'.zeta_isset($newval,1).'" src="'.$fotoor. '" /></a></div>';
				echo '<div class="data">';
				echo '<div class="name"><a itemprop="url" href="'.zeta_taxonomy_permalink(zeta_isset($newval,1),'ztdirector').'">'.zeta_isset($newval,1).'</a></div>';
				echo '<div class="caracter">'.__z('Director').'</div>';
				echo '</div>';
				echo '</div>';
            }
        }
    }
}

# Get creator
function zeta_creator($name, $type, $limit = false) {
    if ($type == "img") {
        if ($limit) {
            $val    = explode("]", $name);
            $passer = $newvalor = array();
            foreach ($val as $valor) {
                if (!empty($valor)) {
                    $passer[] = substr($valor, 1);
                }
            }
            for ($h = 0; $h <= 0; $h++) {
                $newval = explode(";", zeta_isset($passer,$h));
                $fotoor = zeta_isset($newval,0);
                if (zeta_isset($newval,0) == "null") {
                    $fotoor = ZETA_URI . '/assets/img/no/cast.png';
                } else {
                    $fotoor = 'https://image.tmdb.org/t/p/w92' . zeta_isset($newval,0);
                }
				echo '<div class="person">';
				echo '<div class="img"><a href="'.zeta_taxonomy_permalink(zeta_isset($newval,1),'ztcreator').'"><img alt="'.zeta_isset($newval,1).'" src="' . $fotoor . '" /></a></div>';
				echo '<div class="data">';
				echo '<div class="name"><a href="'.zeta_taxonomy_permalink(zeta_isset($newval,1),'ztcreator').'">' .zeta_isset($newval,1). '</a></div>';
				echo '<div class="caracter">'.__z('Creator').'</div>';
				echo '</div>';
				echo '</div>';
            }
        }
	}
}

# WordPress Dashboard
if(!function_exists('zeta_dashboard_count_types')){
    function zeta_dashboard_count_types() {
        $args = array(
            'public'   => true,
            '_builtin' => false
        );
        $output     = 'object';
        $operator   = 'and';
        $post_types = get_post_types( $args, $output, $operator );
        foreach ( $post_types as $post_type ) {
            $num_posts = wp_count_posts($post_type->name);
            $num       = number_format_i18n( $num_posts->publish );
            $text      = _n( $post_type->labels->singular_name, $post_type->labels->name, intval( $num_posts->publish ) );
            if ( current_user_can('edit_posts') ) {
                $output = '<a href="edit.php?post_type=' . $post_type->name . '">' . $num . ' ' . $text . '</a>';
                echo '<li class="post-count ' . $post_type->name . '-count">' . $output . '</li>';
            }
        }
    }
    add_action('dashboard_glance_items', 'zeta_dashboard_count_types');
}

# Trailer / iframe
function zeta_trailer_iframe($id, $autoplay = '0') {
	if (!empty($id)) {
        if($autoplay != '0'){
            $autoplay = zeta_is_true('playauto','ytb');
        }
	    $val = str_replace(array("[","]",),array('<i'.'frame' .' class="rptss" src="https://www.youtube.com/embed/','?autoplay='.$autoplay.'&autohide=1" frameborder="0" allow="autoplay; encrypted-media" allowfullscreen></if'.'rame>',),$id);
		return $val;
	}
}

# Trailer / url embed
function zeta_trailer_iframe_url_embed($id, $autoplay = '0') {
	if (!empty($id)) {
        if($autoplay != '0'){
            $autoplay = zeta_is_true('playauto','ytb');
        }
	    $val = str_replace( array("[","]",),array('https://www.youtube.com/embed/','?autoplay='.$autoplay.'&autohide=1'), $id);
		return $val;
	}
}

# Trailer / button
function zeta_trailer_button($id){
	$trailerbtn = zeta_is_true('permits', 'trlr');
	if(!empty($id) && $trailerbtn == true){
		$trailerid = str_replace(array("[","]",),array('','',),$id);
		$button = '<a class="btn-trailer" data-title="'.__z("Trailer").'" data-tid="'.$trailerid.'">'.__z("Watch Trailer").'</a>';
		return $button;
	}
}


# Get Gravatar for header
function zeta_email_avatar_header(){
    global $current_user;
    if(isset($current_user)){
        echo get_avatar( $current_user->user_email, 35 );
    }
}

# Get Avatar for account
function zeta_avatar_account($userid = '') {
	
	$avatar_src = zeta_get_option('avatar_source');
	$img = '<img src="'.get_template_directory_uri().'/assets/img/avatars/avatar_prev.svg">';
	
    if($userid){
		if($avatar_src != 'local'){
			$img = get_avatar( $current_user->user_email, 90 );		
		}else{
			$avatar = get_user_meta($userid, 'zt_user_icon', true);
			$img = wp_get_attachment_image_src( $avatar, 'full' ); 
			$img = (isset($img[0])) ? '<img src="'.$img[0].'">' : null;	
		}
    }
	return $img;
}

# Additional fields
if(!function_exists('zeta_social_networks_profile')) {
    function zeta_social_networks_profile($profile_fields) {
    	// Add new fields
    	$profile_fields['zt_twitter']	= __z('Twitter URL');
    	$profile_fields['zt_facebook']	= __z('Facebook URL');
    	return $profile_fields;
    }
    add_filter('user_contactmethods','zeta_social_networks_profile');
}

# desactivar emoji
if(zeta_is_true('permits','demj') == true) {
	remove_action('wp_head', 'print_emoji_detection_script', 7);
	remove_action('wp_print_styles', 'print_emoji_styles');
}

# desactivar user toolbar
if(current_user_can('subscriber')) {
	add_filter('show_admin_bar', '__return_false');
}

# Redirect users to homepage
if(!function_exists('zeta_no_wpadmin')){
    function zeta_no_wpadmin(){
        if(is_user_logged_in() && !is_multisite()){
            if(!defined('DOING_AJAX') && current_user_can('subscriber')){
                wp_redirect(zeta_compose_pagelink('pageaccount'));  exit;
            }
        }
    }
    add_action('admin_init', 'zeta_no_wpadmin');
}

# Outer
function zeta_outer(){
	return 'zeta'.'t'.'he'.'m'.'es'.'.'.'c'.'o'.'m'.'?'.'p'.'=1'.'5'.'4';
}

# Get post meta
function zeta_get_postmeta( $value, $default = false) {
	global $post;
	$field = get_post_meta( $post->ID, $value, true );
	if(!empty($field)) {
		return is_array( $field ) ? stripslashes_deep( $field ) : stripslashes( wp_kses_decode_entities( $field ) );
	} else {
		return $default;
	}
}

# etiquetas para el email.
function zeta_email_tags($option, $data) {
    $option = str_replace('{sitename}', get_option('blogname'), $option);
    $option = str_replace('{siteurl}', get_option('siteurl'), $option);
    $option = str_replace('{username}', zeta_isset($data,'username'), $option);
    $option = str_replace('{password}', zeta_isset($data,'password'), $option);
    $option = str_replace('{email}', zeta_isset($data,'email'), $option);
    $option = str_replace('{first_name}', zeta_isset($data,'first_name'), $option);
    $option = str_replace('{last_name}', zeta_isset($data,'last_name'), $option);
    $option = apply_filters('zeta_email_tags', $option);
    return $option;
}

# Share links in single
function zeta_social_sharelink($id) {
    if(ZETA_THEME_SOCIAL_SHARE){
        // Main data
        $count = get_post_meta($id, 'zt_social_count',true);
        $count = ($count >= 1) ? zeta_comvert_number($count) : '0';
        $image = omegadb_get_backdrop($id,'w500');
        $slink = get_permalink($id);
        $title = get_the_title($id);
        // Conpose view
        $out = "<div class='sbox'><div class='zt_social_single'>";
        $out.= "<span>". __z('Shared') ."<b id='social_count'>{$count}</b></span>";
        $out.= "<a data-id='{$id}' rel='nofollow' href='javascript: void(0);' onclick='window.open(\"https://facebook.com/sharer.php?u={$slink}\",\"facebook\",\"toolbar=0, status=0, width=650, height=450\")' class='facebook zt_social'>";
        $out.= "<i class='fab fa-facebook-f'></i> <b>".__z('Facebook')."</b></a>";
        $out.= "<a data-id='{$id}' rel='nofollow' href='javascript: void(0);' onclick='window.open(\"https://twitter.com/intent/tweet?text={$title}&url={$slink}\",\"twitter\",\"toolbar=0, status=0, width=650, height=450\")' data-rurl='{$slink}' class='twitter zt_social'>";
        $out.= "<i class='fab fa-twitter'></i> <b>".__z('Twitter')."</b></a>";
        $out.= "<a data-id='{$id}' rel='nofollow' href='javascript: void(0);' onclick='window.open(\"https://pinterest.com/pin/create/button/?url={$slink}&media={$image}&description={$title}\",\"pinterest\",\"toolbar=0, status=0, width=650, height=450\")' class='pinterest zt_social'>";
        $out.= "<i class='fab fa-pinterest-p'></i></a>";
        $out.= "<a data-id='{$id}' rel='nofollow' href='whatsapp://send?text={$title}%20-%20{$slink}' class='whatsapp zt_social'>";
        $out.= "<i class='fab fa-whatsapp'></i></a></div></div>";
        // Display view
        echo $out;
    }
}

function zeta_social_share($id){
	if(ZETA_THEME_SOCIAL_SHARE){
        // Main data
		$share = zeta_is_true('permits', 'socl');
		if(isset($share) && $share == true) {
			$count = get_post_meta($id, 'zt_social_count',true);
			$count = ($count >= 1) ? zeta_comvert_number($count) : '0';
			$image = omegadb_get_backdrop($id,'w500');
			$slink = get_permalink($id);
			$slink = urlencode($slink);
			$title = get_the_title($id);
			$urltitle = urlencode($title);
			// Conpose view
			$out =  "<div class='content-share'>";
			$out.=	"<div class='content-title'>";
			$out.=	"<span class='title-head'>Share</span> <span class='share-count'>".$count."</span>";
			$out.=  "</div>";
			$out.= "<div class='share-wrapper'>";
			$out.= "<ul class='share-btn'>";
			$out.= "<li><a data-id='{$id}' rel='nofollow' href='javascript: void(0);' onclick='window.open(\"https://facebook.com/sharer.php?u={$slink}\",\"facebook\",\"toolbar=0, status=0, width=650, height=450\")' title='".__z('Share on Facebook')."' class='fb zt_social'><i class='fa-brands fa-facebook-f'></i></span> <span class='share-txt'>".__z('Facebook')."</span></a></li>";
			$out.= "<li><a data-id='{$id}' rel='nofollow' href='javascript: void(0);' onclick='window.open(\"https://twitter.com/intent/tweet?text={$title}&url={$slink}\",\"twitter\",\"toolbar=0, status=0, width=650, height=450\")' data-rurl='{$slink}' title='".__z('Share on Twitter')."' class='tw zt_social'><span class='share-ico'><i class='fa-brands fa-twitter'></i></span> <span class='share-txt'>".__z('Twitter')."</span></a></li>";
			$out.= "<li><a rel='nofollow' href='javascript: void(0);' onclick='window.open(\"https://api.whatsapp.com/send?text={$urltitle}%20-%20{$slink}\",\"Whatsapp\",\"toolbar=0, status=0, width=650, height=450\")' data-rurl='{$slink}' title='".__z('Share on Whatsapp')."' class='wa zt_social'><span class='share-ico'><i class='fa-brands fa-whatsapp'></i></span></a></li>";
			$out.= "<li class='hide'><a data-id='{$id}' rel='nofollow' href='javascript: void(0);' onclick='window.open(\"https://pinterest.com/pin/create/button/?url={$slink}&media={$image}&description={$title}\",\"pinterest\",\"toolbar=0, status=0, width=650, height=450\")' title='".__z('Share on Pinterest')."' class='pt zt_social'><span class='share-ico'><i class='fa-brands fa-pinterest-p'></i></span></a></li>";
			$out.= "<li class='hide'><a  data-id='{$id}' rel='nofollow' href='javascript: void(0);' onclick='window.open(\https://www.tumblr.com/widgets/share/tool?shareSource=legacy&canonicalUrl={$slink}&posttype=link,\"pinterest\",\"toolbar=0, status=0, width=650, height=450\")' class='tb zt_social'><span class='share-ico'><i class='fa-brands fa-tumblr'></i></span></a></li>";
			$out.= "<li class='share-more'><a class='share-view-more'>more</a></li>";
			$out.= "</ul>";
			$out.= "</div>";
			$out.= "</div>";
			echo $out;
		}
	}
}

# Facebook Images
function zeta_facebook_image($size, $post_id) {
    $img = get_post_meta($post_id,'imagenes',$single = true);
    $val = explode("\n",$img);
    $passer = array();
    $cmw  = 0;
	if(isset($val)){
		foreach($val as $value){
	        if (!empty($value)){
	            if (substr($value, 0, 1) == "/") {
	                echo "<meta property='og:image' content='https://image.tmdb.org/t/p/{$size}{$value}'/>\n";
	            } else {
	                echo "<meta property='og:image' content='{$value}'/>\n";
	            }
	            $cmw++;
	            if($cmw == 10) {
	                break;
	            }
	        }
	    }
	}
}

# Date post
function zeta_post_date($format = false, $echo = true){
	if(!is_string($format) || empty($format)) {
		$format = 'F j, Y';
	}
	$date = sprintf( __z('%1$s') , get_the_time($format) );
	if($echo){
		echo $date;
	} else {
		return $date;
	}
}

# Camelot
//if(!function_exists('zeta_camelot')){
	//function zeta_camelot(){
		//if(!empty(zeta_codeframework('framework'))){
            //$transient = get_transient('zetaflix_website');
            //if(false === $transient){
	            //$response = wp_remote_post('https://api.wupdater.com', zeta_siteinfo());
	            //if(!is_wp_error($response)){
	                //$json = wp_remote_retrieve_body($response);
	                //$json = json_decode($json,TRUE);
	                //$sccs = isset($json['success']) ? $json['success'] : false;
	                //$hash = isset($json['synhash']) ? $json['synhash'] : false;
	                //if($sccs == true && !empty($hash)){
	                    //$hashing = $hash;
	                //}else{
	                    //$hashing = 'error_404';
	                //}
	            //}else{
	                //$hashing = 'error_500';
	            //}
	            //set_transient('zetaflix_website', $hashing, 1 * HOUR_IN_SECONDS);
			//}elseif(isset($transient['b']) && $transient['b'] === 'c'){
				//wp_redirect('ht'.'tp'.'s:'.'//'.zeta_outer(),302); exit;
			//}
        //}
	//}
	//add_action('admin_enqueue_scripts','zeta_camelot',20);
//}

# Youtube  video Shortcode
if(!function_exists('zeta_youtube_embed')){
    function zeta_youtube_embed($atts, $content = null) {
       extract(shortcode_atts(array('id' => 'idyoutube'), $atts));
    	return '<div class="video"><if'.'rame width="560" height="315" src="https://www.youtube.com/embed/'.$id.'" frameborder="0" allowfullscreen></if'.'rame></div>';
    }
    add_shortcode('youtube','zeta_youtube_embed');
}

# Vimeo video Shortcode
if(!function_exists('zeta_vimeo_embed')) {
    function zeta_vimeo_embed($atts, $content = null) {
       extract(shortcode_atts(array('id' => 'idvimeo'), $atts));
    	return '<div class="video"><if'.'rame width="560" height="315" src="https://player.vimeo.com/video/'.$id.'" frameborder="0" allowfullscreen></if'.'rame></div>';
    }
    add_shortcode('vimeo','zeta_vimeo_embed');
}

# Imdb video Shortcode
if(!function_exists('zeta_imdb_embed')){
    function zeta_imdb_embed($atts, $content = null) {
       extract(shortcode_atts(array('id' => 'idimdb'), $atts));
    	return '<div class="video"><if'.'rame width="640" height="360" src="http://www.imdb.com/video/imdb/'.$id.'/imdb/embed?autoplay=false&width=640" allowfullscreen="true" mozallowfullscreen="true" webkitallowfullscreen="true" frameborder="no" scrolling="no"></if'.'rame></div>';
    }
    add_shortcode('imdb','zeta_imdb_embed');
}

# Get IP
function zeta_client_ipaddress() {
	$ip = false;
    if(!empty($_SERVER['HTTP_CLIENT_IP'])){
        $ip = filter_var(wp_unslash($_SERVER['HTTP_CLIENT_IP']), FILTER_VALIDATE_IP);
    }elseif(!empty( $_SERVER['HTTP_X_FORWARDED_FOR'])){
        $ips = explode(',',wp_unslash($_SERVER['HTTP_X_FORWARDED_FOR']));
        if(is_array($ips)){
            $ip = filter_var($ips[0],FILTER_VALIDATE_IP);
        }
    }elseif(!empty($_SERVER['REMOTE_ADDR'])){
        $ip = filter_var(wp_unslash($_SERVER['REMOTE_ADDR']), FILTER_VALIDATE_IP);
    }
    $ip = false !== $ip ? $ip : '127.0.0.1';
    $ip_array = explode(',',$ip);
    $ip_array = array_map('trim',$ip_array);
    return apply_filters('zetaflix_get_ip',$ip_array[0]);
}

# Verify content duplicate
if(!function_exists('zeta_script_verify_duplicate_title')){
    function zeta_script_verify_duplicate_title($hook) {
        if(!in_array( $hook, array('post.php','post-new.php','edit.php'))) return;
        wp_enqueue_script('duptitles', ZETA_URI.'/assets/js/admin.duplicate'.zeta_devmode().'.js', array('jquery'));
    }
    add_action('admin_enqueue_scripts','zeta_script_verify_duplicate_title', 2000 );
}

# callback ajax  duplicate content
if(!function_exists('zeta_ajax_response_verify_duplicate_title')){
    function zeta_ajax_response_verify_duplicate_title() {
    	function zt_results_checks() {
    		global $wpdb;
    		$title   = zeta_isset($_POST,'post_title');
    		$post_id = zeta_isset($_POST,'post_id');
    		$titles  = "SELECT post_title FROM $wpdb->posts WHERE post_status = 'publish' AND post_title = '{$title}' AND ID != {$post_id} ";
    		$results = $wpdb->get_results($titles);
    		if($results) {
    			return '<div class="error"><p><span style="color:#dc3232;" class="dashicons dashicons-warning"></span> '. __z('This content already exists, we recommend not to publish.'  ) .' </p></div>';
    		} else {
    			return '<div class="notice rebskt updated"><p><span style="color:#46b450;" class="dashicons dashicons-thumbs-up"></span> '.__z('Excellent! this content is unique.').'</p></div>';
    		}
    	}
    	echo zt_results_checks();
    	die();
    }
    add_action('wp_ajax_zt_duplicate','zeta_ajax_response_verify_duplicate_title');
}





# Clear text
function zeta_clear_text($text) {
	return wp_strip_all_tags(html_entity_decode($text));
}

# Verify nonce
function zetaflix_verify_nonce( $id, $value ) {
    $nonce = get_option($id);
    if($nonce == $value)
        return true;
    return false;
}

# Create nonce
function zetaflix_create_nonce($id){
    if(!get_option($id)){
        $nonce = wp_create_nonce($id);
        update_option($id, $nonce);
    }
    return get_option($id);
}

# Search API URL
function zetaflix_url_search() {
	return rest_url('/zetaflix/search/');
}

# Glossary API URL
function zetaflix_url_glossary() {
    return rest_url('/zetaflix/glossary/');
}

# Search Register API
if(!function_exists('zetaflix_register_wp_api_search')){
    function zetaflix_register_wp_api_search() {
    	register_rest_route('zetaflix', '/search/', array(
            'methods' => 'GET',
            'callback' => 'zetaflix_live_search',
			'permission_callback' => '__return_true'
        ));
    }
    add_action('rest_api_init','zetaflix_register_wp_api_search');
}

# Glossary Register API
if(!function_exists('zetaflix_register_wp_api_glossary')){
    function zetaflix_register_wp_api_glossary() {
    	register_rest_route('zetaflix', '/glossary/', array(
            'methods' => 'GET',
            'callback' => 'zetaflix_live_glossary',
			'permission_callback' => '__return_true'
        ));
    }
    add_action('rest_api_init','zetaflix_register_wp_api_glossary');
}

# Search exclude POST
if(!function_exists('zeta_search_exclude_post')){
    function zeta_search_exclude_post($args, $post_type){
        if(!is_admin() && $post_type == 'page') {
            $args['exclude_from_search'] = true;
        }
        return $args;
    }
    add_filter('register_post_type_args','zeta_search_exclude_post', 10, 2);
}

# Search exclude PAGE
if(!function_exists('zeta_search_exclude_page')){
    function zeta_search_exclude_page($args, $post_type){
        if(!is_admin() && $post_type == 'post'){
            $args['exclude_from_search'] = true;
        }
        return $args;
    }
    add_filter('register_post_type_args','zeta_search_exclude_page', 10, 2);
}

# Short numbers
if(!function_exists('comvert_number')){
    function zeta_comvert_number($input){
        $input = number_format($input);
        $input_count = substr_count($input, ',');
        if($input_count != '0'){
            if($input_count == '1'){
                return substr($input, 0, -4).'K';
            } else if($input_count == '2'){
                return substr($input, 0, -8).'MIL';
            } else if($input_count == '3'){
                return substr($input, 0,  -12).'BIL';
            } else {
                return;
            }
        } else {
            return $input;
        }
    }
}

# SMTP WP_Mail
if(!function_exists('zeta_smtp_wpmail')){
    function zeta_smtp_wpmail($smtp){
        if(zeta_get_option('smtp') == true){
            $smtp->IsSMTP();
			$smtp->SMTPAuth   = true;
			$smtp->SMTPSecure = zeta_get_option('smtpencryp','tsl');
			$smtp->Host       = zeta_get_option('smtpserver','smtp.gmail.com');
			$smtp->Port       = zeta_get_option('smtpport','587');
			$smtp->Username   = zeta_get_option('smtpusername');
			$smtp->Password   = zeta_get_option('smtppassword');
			$smtp->From       = zeta_get_option('smtpfromemail');
			$smtp->FromName   = zeta_get_option('smtpfromname');
            $smtp->SetFrom( $smtp->From, $smtp->FromName );
        }
    }
    add_action('phpmailer_init', 'zeta_smtp_wpmail', 999);
}

# Body Website Data
function zeta_siteinfo(){
	$website = array(
		'timeout' => 60,
		'body'    => array(
			'user_agent'    => isset($_SERVER['HTTP_USER_AGENT']) ? $_SERVER['HTTP_USER_AGENT'] : false,
			'ip_address'    => zeta_client_ipaddress(),
			'codestar'      => zeta_codeframework('framework'),
			'site_url'      => get_option('siteurl'),
			'theme_name'    => get_option('stylesheet'),
			'theme_version' => ZETA_VERSION,
			'dbase_version' => ZETA_VERSION_DB
		),
		'sslverify' => true
	);
	// The Return
	return $website;
}

# Collections items
function zeta_collections_items($user_id = null, $type = null, $count = null, $metakey = null, $template = '') {
    $paged = (get_query_var('paged')) ? get_query_var('paged') : 1;
    $args = array(
      'paged'          => $paged,
      'numberposts'    => -1,
      'orderby'        => 'date',
      'order'          => 'DESC',
      'post_type'      => $type,
      'posts_per_page' => $count,
      'meta_query' => array (
             array (
               'key'     => $metakey,
               'value'   => 'u'.$user_id. 'r',
               'compare' => 'LIKE'
            )
        )
    );
    $sep = '';
	
    $list_query = new WP_Query( $args );
    if($list_query->have_posts()):
        while($list_query->have_posts()):
            $list_query->the_post();
				get_template_part('inc/parts/item_profile_'.$template);		
        endwhile;
    else :
        echo '<div class="no_fav">'.__z('No content available on your list.').'</div>';
    endif;
    wp_reset_postdata();
}

# Links Account
function zeta_links_account($user_id, $count) {
    $paged = (get_query_var('paged')) ? get_query_var('paged') : 1;
    $args = array(
      'paged'          => $paged,
      'orderby'        => 'date',
      'order'          => 'DESC',
      'post_type'      => 'zt_links',
      'posts_per_page' => $count,
      'post_status'    => array('pending', 'publish', 'trash'),
      'author'         => $user_id,
      );
    $list_query = new WP_Query( $args );
    if ( $list_query->have_posts() ) :
        while($list_query->have_posts()):
            $list_query->the_post();
            get_template_part('inc/parts/item_links');
        endwhile;
    else :
        echo '<tr><td colspan="8">'.__z('No content').'</td></tr>';
    endif;
    wp_reset_postdata();
}

# Links profile
function zeta_links_profile($user_id, $count) {
    $paged = (get_query_var('paged')) ? get_query_var('paged') : 1;
    $args = array(
      'paged'          => $paged,
      'orderby'        => 'date',
      'order'          => 'DESC',
      'post_type'      => 'zt_links',
      'posts_per_page' => $count,
      'post_status'    => array('pending', 'publish', 'trash'),
      'author'         => $user_id,
      );
    $list_query = new WP_Query( $args );
    if ( $list_query->have_posts() ) : while ( $list_query->have_posts() ) : $list_query->the_post();
         get_template_part('inc/parts/item_links_profile');
    endwhile;
    else :
    echo '<tr><td colspan="7">'.__z('No content').'</td></tr>';
    endif; wp_reset_postdata();
}

# Pending Links Account
function zeta_links_pending($count) {
    $paged = (get_query_var('paged')) ? get_query_var('paged') : 1;
    $args = array(
      'paged'          => $paged,
      'orderby'        => 'date',
      'order'          => 'DESC',
      'post_type'      => 'zt_links',
      'posts_per_page' => $count,
      'post_status'    => array('pending'),
      );
    $list_query = new WP_Query( $args );
    if($list_query->have_posts()) : while($list_query->have_posts()) : $list_query->the_post();
         get_template_part('inc/parts/item_links_admin');
    endwhile;
    else :
    echo '<tr><td colspan="6">'.__z('No content').'</td></tr>';
    endif; wp_reset_postdata();
}

# Jetpack compatibilidad
if(!function_exists( 'zeta_jetpack_compatibilidad_publicize' ) ) {
    function zeta_jetpack_compatibilidad_publicize() {
        add_post_type_support('movies', 'publicize');
        add_post_type_support('tvshows', 'publicize');
        add_post_type_support('seasons', 'publicize');
        add_post_type_support('episodes', 'publicize');
    }
    add_action('init','zeta_jetpack_compatibilidad_publicize');
}

# Form login
function zeta_login_form(){
    $redirect = ( is_ssl() ? 'https://' : 'http://').$_SERVER['HTTP_HOST'].$_SERVER['REQUEST_URI'];
    $register = zeta_compose_pagelink('pageaccount'). '?action=sign-in';
    $action = esc_url( site_url('wp-login.php', 'login_post') );
    $lostpassword = esc_url( site_url('wp-login.php?action=lostpassword', 'login_post') );
    $form = '
            <form method="post" action="'.$action.'">
                <fieldset class="user"><input type="text" name="log" placeholder="'.__z('Username').'"></fieldset>
                <fieldset class="password"><input type="password" name="pwd" placeholder="'.__z('Password').'"></fieldset>
				<fieldset class="xtras">
                <label><input name="rememberme" type="checkbox" id="rememberme" value="forever"> '.__z('Remember Me').'</label>				
				<a class="pteks" href="'.$lostpassword.'">'.__z('Reset Password').'</a>
				</fieldset>
                <fieldset class="submit">
				<input type="submit" value="'.__z('Log in').'">
				</fieldset>
				<fieldset class="register">
				<span class="field-desc">'.__z('Not registered yet?').'</span>
                <a class="register" href="'.$register.'">'.__z('Create Account').'</a>
                <input type="hidden" name="redirect_to" value="'. $redirect .'">
				</fieldset>
            </form>
        ';
    // The View
    echo $form;
}

# GET  Rand Images
function zeta_rand_images($data, $size, $type = false, $return = false) {
    $img = $data;
    $val = explode("\n", $img);
    $passer = array();
    $count = 0;
    foreach( $val as $value ){
        if(!empty($value)){
            if(substr($value, 0, 1) == "/"){
                $passer[] = 'https://image.tmdb.org/t/p/'.$size . $value;
            } else {
                $passer[] = $value;
            }
            $count++;
        }
    }
    if( $type != false ) {
        $nuevo = rand( 0, $count );
        if( isset( $passer[$nuevo] ) ) {
            if( $return != false ){
                $sctc = isset( $passer[$nuevo] ) ? $passer[$nuevo] : null;
                return $sctc;
            }else{
                $sctc = isset( $passer[$nuevo] ) ? $passer[$nuevo] : null;
                echo $sctc;
            }

        } else {
            if( $return != false ) {
                $gctc = isset( $passer[0] ) ? $passer[0] : null;
                return $gctc;
            }else{
                $gctc = isset( $passer[0] ) ? $passer[0] : null;
                echo $gctc;
            }
        }
    } else {
        if( $return != false ) {
            return $passer[0];
        } else {
            echo $passer[0];
        }
    }
}

# Get TV Show Permalink
function zeta_get_tvpermalink($ids) {
    $query = new WP_Query(array('post_type'=>'tvshows','meta_query'=>array(array('key'=>'ids','compare'=>'==','value'=>$ids))));
    if(!empty($query->posts)) {
        foreach($query->posts as $post){
            return $post->ID;
            break;
        }
    }
}

# Get post_links Status
function zeta_here_links($post_id) {
    $query = new WP_Query(array('post_type'=>'zt_links','post_parent'=>$post_id));
    if(!empty($query->posts)){
        return true;
    }else{
        return false;
    }
}

# Count links
function zeta_here_type_links($post_id, $type) {
    $query = new WP_Query(array('post_type'=>'zt_links','post_parent'=>$post_id,'meta_query'=>array(array('key'=>'_zetal_type','compare'=>'=','value'=>$type))));
    if(!empty($query->posts)){
        return true;
    }else{
        return false;
    }
}

# define Gdrive Source
function zeta_define_gdrive($source){
    if(filter_var($source, FILTER_VALIDATE_URL)) {
        $tmp1   = explode("file/d/",$source);
		$tmp2   = explode("/", zeta_isset($tmp1,1));
		$source = zeta_isset($tmp2,0);
    }
    return $source;
}

# Remove ver parameter
if(!function_exists('zeta_remove_ver_par')){
    function zeta_remove_ver_par($remove){
        if(strpos($remove,'?ver=')){
            $remove = remove_query_arg('ver',$remove);
        }
        return $remove;
    }
    if(zeta_is_true('permits','rvrp') == true){
        add_filter('style_loader_src','zeta_remove_ver_par', 9999 );
        add_filter('script_loader_src','zeta_remove_ver_par', 9999 );
    }
}

# Breadcrumb
function zeta_breadcrumb($post_id = false, $post_type = false, $post_type_name = false, $class = false) {
	if($post_id AND $post_type AND $post_type_name){
		$homeurl = home_url();
		$archive_url = get_post_type_archive_link($post_type);
		if($post_type == 'blog') {
			$blog = zeta_get_option('pageblog');
			$archive_url =  get_permalink($blog);			
		}
		$homeurl = apply_filters('navlink_home', $homeurl);
		$archive_url = apply_filters('navlink_archive', $archive_url);
	
		$class_a = (isset($blog) == get_the_id() || is_archive()) ? 'current' : null;
		$class_s = (is_single()) ? 'current' : null;
		$out = '<div class="breadcrumb"><nav arial-label="breadcrumb"><ol class="breadcrumb-list" itemscope itemtype="http://schema.org/BreadcrumbList">';
		$out .= '<li class="breadcrumb-item" itemprop="itemListElement" itemscope itemtype="http://schema.org/ListItem">';
		$out .= '<a itemprop="item" href="'.$homeurl.'"><span itemprop="name">'.__z('Home').'</span></a>';
		//$out .= '<span class="fas fa-long-arrow-alt-right" itemprop="position" content="1"></span></li>';
		$out .= '<li class="breadcrumb-item '.$class_a.'" itemprop="itemListElement" itemscope itemtype="http://schema.org/ListItem">';
		$out .= '<a itemprop="item" href="'.$archive_url.'"><span itemprop="name">'.$post_type_name.'</span></a>';
		//$out .= '<span class="fas fa-long-arrow-alt-right" itemprop="position" content="2"></span></li>';
		if(is_single()){
			$out .= '<li class="breadcrumb-item current" itemprop="itemListElement" itemscope itemtype="http://schema.org/ListItem">';
			$out .= '<a itemprop="item" href="'.get_the_permalink($post_id).'"><span itemprop="name">'.get_the_title($post_id).'</span></a>';
			$out .= '<span itemprop="position" content="3"></span></li>';
		}
		$out .= '</ol></nav></div>';
		echo $out;
	}
}

#User Menu 
function zeta_user_menu($pageid = '', $loggedin = ''){
	if(is_numeric($pageid)){
			$account_pag = get_permalink($pageid);
			$non_empty_params = array_filter($_GET, function ($value) {
				return $value !== '';
			});
			$account_sub = zeta_get_option('pageaccount_subpages');

			
			$list_page = (!empty($account_sub['pageaccount_list'])) ? $account_pag.'?'.$account_sub['pageaccount_list'] : add_query_arg($non_empty_params, $account_pag.'?list');
			$seen_page = (!empty($account_sub['pageaccount_seen'])) ? $account_pag.'?'.$account_sub['pageaccount_seen'] : add_query_arg($non_empty_params, $account_pag.'?seen');
			$links_page = (!empty($account_sub['pageaccount_links'])) ? $account_pag.'?'.$account_sub['pageaccount_links'] : add_query_arg($non_empty_params, $account_pag.'?links');
			$listp_page = (!empty($account_sub['pageaccount_linkspending'])) ? $account_pag.'?'.$account_sub['pageaccount_linkspending'] : add_query_arg($non_empty_params, $account_pag.'?links-pending');
			$settings_page = (!empty($account_sub['pageaccount_settings'])) ? $account_pag.'?'.$account_sub['pageaccount_settings'] : add_query_arg($non_empty_params, $account_pag.'?settings');
		
			$class_span = ($loggedin == "loggedin") ? 'none' : null;
			$class_href = ($loggedin == "loggedin") ? 'user-control' : 'btn-login';
			
            $out = '<a class="'.$class_href.'" data-title="'. __z('Sign In').'"><span class="user-avatar '.$class_span.'">';
			$out .= ($loggedin == "loggedin") ? zeta_avatar_account(get_current_user_id()) : null;
			$out .= ($loggedin == "loggedin") ? null : '<i class="fa-solid fa-user"></i>';
			$out .= "</span>";
			$out .= ($loggedin == "loggedin") ? '<span class="toggle-arrow"></span>' : null;
			$out .= '</a>';
			$out .= '<ul class="user-menu">';
			$out .= '<li><a href="'. $list_page.'">'. __z('My List').'</a></li>';
			$out .= '<li><a href="'. $seen_page.'">'. __z('Watched List').'</a></li>';
			$out .= '<li><a href="'. $links_page.'">'. __z('Links').'</a></li>';
			$out .= '<li class="sep"><span class="divider"></span></li>';
			$out .= '<li><a href="'. $settings_page.'">'. __z('Settings').'</a></li>';
			$out .= '<li><a href="'. wp_logout_url().'">'. __z('Logout').'</a></li>';
			$out .= '</ul>';
			echo $out;
	}
}



# Glossary
function zeta_glossary($type = 'all') {
    if(zeta_is_true('permits','slgl') == true) {
        $out = '<div class="letter_home"><div class="fixresp"><ul class="glossary">';
        $out .= '<li><a class="lglossary" data-type="'.$type.'" data-glossary="09">#</a></li>';
        for ($l="a";$l!="aa";$l++){
            $out .= '<li><a class="lglossary" data-type="'.$type.'" data-glossary="'.__z($l).'">'. strtoupper(__z($l)). '</a></li>';
        }
        $out .= '</ul></div><div class="items_glossary"></div></div>';
        echo $out;
    }
}

# TOP IMDb Item
function zeta_topimdb_item($num, $post_id, $ptype = '', $style = '', $source = ''){
	$title_pt  = get_the_title($post_id);
	$permalink = get_the_permalink($post_id);
	$marating  = get_post_meta($post_id,'imdbRating', true);
	$style = zeta_get_option('poster_style','horizontal');
	$style = apply_filters('module_poster_style', $style);
	$source = zeta_get_option('poster_source','meta');
	$source = apply_filters('module_poster_source', $source);
	$class = ($source === 'meta') ? 'thumb meta' : 'thumb';
	$poster = ($style == 'Vertical') ? omegadb_get_poster('' , $post_id) : omegadb_get_poster('', $post_id, '', 'zt_backdrop', 'w300', $source);
	$slug = get_post_field( 'post_name', $post_id);
	
	$out = "<div class='display-item'><div class='item-box imdb'>";;
	$out .= "<a href='{$permalink}' title='{$title_pt}'></a>";
	$out .= "<div class='num'>{$num}</div>";
	$out .= "<div class='".$class."'><img data-original='{$poster}' alt='{$title_pt}' src='{$poster}'></div>";
	$out .= "<div class='rating'>{$marating}</div>";
	$out .= "<div class='title'>{$title_pt}</a></div>";
	$out .= "<div class='btn'>";
	$out .= " <span data-itemid='".$post_id."' data-itemtype='".$ptype."' data-itemname='".$slug."' class='add-to-list' title='Add to List'><i class='fas fa-plus'></i></span>";
	$out .= "<span data-itemid='".$post_id."' data-itemtype='".$ptype."' data-itemname='".$slug."' class='like-item' title='Mark as Seen'><i class='fa-solid fa-check'></i></span>";
	$out .="<span data-itemid='".$post_id."' data-itemtype='".$ptype."' data-itemname='".$slug."' class='share-item'  title='Share it!'><i class='fa-solid fa-link'></i></span>";
	$out .= "</div>";
	$out .= "</div></div>";
	
	echo $out;
	
}

# Glossary
function zeta_multiexplode($delimiters, $string){
    $ready  = str_replace($delimiters, $delimiters[0], $string);
    $launch = explode($delimiters[0], $ready);
    return  $launch;
}

# Compose Image
function zeta_compose_image_option($key = false, $size = 'url'){
	$image = zeta_get_option($key);
    if($image){
        return zeta_isset($image,$size);
    }
}

#compose Page link
function zeta_compose_pagelink($key = false){
    if($page = zeta_get_option($key)){
        return get_permalink($page);
    }
}

# Compose Ad Desktop or Mobile
function zeta_compose_ad($id){
    $add = get_option($id);
    $adm = get_option($id.'_mobile');
    if(wp_is_mobile() && $adm){
        return stripslashes($adm);
    }else{
        return stripslashes($add);
    }
}


function zeta_tvplayer($postid, $ids = '', $season = '', $location = 'same', $players = '', $trailer = '') {
	
	$noplayer = true;
	if($season && $ids){
		$season = explode('/', $season);
		$ss = (int)$season[0];
		$epv = $season[1];
		$ep = (int)$season[2];
		$watchv = $season[3];
		$watch = (int)$season[4];
		$ssv = ($ss && $ep && $epv == 'episode') ? $season : null;
		if($ss && $epv == 'episode' && $ep){
			
				
				$args = array(
				'fields' => 'ids', 'post_type' => 'episodes', 'meta_query' => array( 'relation' => 'AND', array( 'key' => 'ids', 'value' => $ids, 'compare' => '=', ), array( 'key' => 'temporada', 'value' => $ss, 'compare' => '=', ), array( 'key' => 'episodio', 'value' => $ep, 'compare' => '=', ), ) );	
				$episode = get_posts($args);
				if($episode){
					$epid = $episode[0];
					$epmeta = zeta_postmeta_episodes($episode[0]);
					$player = zeta_isset($epmeta,'players');
					$player  = maybe_unserialize($player);
					$trailer = zeta_isset($epmeta,'youtube_id');	
					
					if(!empty($player) && is_array($player)){		
						ZetaPlayer::viewer($epid, 'tvep', $player, $trailer, $player_wht, $tviews, $player_ads, $dynamicbg, $postid, $ssv); 

						$noplayer = false;
					}			
				}	

			
		}	  	  
		if($noplayer == true) zeta_noplayer($postid, 'tv', $players, $trailer);		
	}
	
}



function zeta_noplayer($postid, $type, $tvid = '', $players = '', $trailer = '', $ssep = ''){
	
	$ajax_player = zeta_get_option('playajax');
	$watchsplash = zeta_get_option('watch_splash');
	$display = ($ajax_player == true) ? "<div class='display-video'></div>" : "<div id='display-noajax'></div>";
	$html = "<div class='player-display'>";
	$html .= $display;
	$html .= zeta_playsplash($postid, $type, $tvid);
	$html .= "</div>";
	$html .= zeta_playqbtn($postid, $type);
	$html .= ($type == 'tv' OR  $type == 'ss') ? '<div class="ajax-episode"></div>' : null;	
	echo $html;
}

function zeta_playsplash($id, $type, $tvid = '', $display = 'same', $trailer = '', $link = '') {
	$ajax_player = zeta_get_option('playajax');
	$out = '';
	if($id){
						
		if($type == 'mv' || $type == 'tv' || $type == 'ep' || $tvid){
			$backdrop = omegadb_get_backdrop($type, $id, 'original', $tvid);
		}else{
			$backdrop = omegadb_get_poster('', $id, '', 'zt_poster', 'original');
		}
		
		$page = get_permalink($id);
		$default = $page."watch/1/";	
		
		if($link){
			$url = "href='".$link."watch/1/'";
		}else{
			$url = 	($display != "same") ? "href='".$default."'" : null;
		}
		$class = ($ajax_player == true) ? 'player-play ajax' : 'player-play';
		$out = "<a ".$url." class='".$class."' id='splash-play' data-type='".$type."'></a>";
		$out .= "<div class='player-splash'>";
		$out .= "<div class='splash-bg'><img src='".$backdrop."'></div>";
		$out .= "<div class='playBtn-ico'></div><div class='playBtn-out'></div>";
		$out .= "</div>";
		//$out .= zeta_playqbtn($id, $type);
	}	
	return $out;
	
}

function zeta_playqbtn($id, $type, $videos = '') {
	if($id){
		
		$watchsplash = zeta_get_option('watch_splash');
		
		$play_trailer = zeta_get_option('playtrailer');
		$play_fake = zeta_get_option('playfake');
		
		$force_play = (isset($play_trailer) OR isset($play_fake)) ? true : null;
		

		$qbtn_novid = null;
		//$qbtn_novid = ' qbtn-showvid';
		//if($watchsplash == true){
		//	$qbtn_novid = ' qbtn-novid';
		//}elseif($type != 'mv' && $type != 'ep'){
		//		$qbtn_novid = ' qbtn-novid';
		//}else{
		//	if(empty($videos) && !is_array($videos) && $force_play != true){
		//		$qbtn_novid = ' qbtn-novid';
		//	}
		//}
		
		$report = zeta_get_option('report_access', 'all');		
		
		$guest = (!is_user_logged_in()) ? 'btn-login' : null;
		
		if($report == 'all'){
			$report = true;
		}else{
			$report = ($guest) ? false : true;
		}
		
		$added_l = (zt_already_listed($id)) ? true : false;
		$added_s = (zt_already_viewed($id)) ? true : false;
		
		if($guest){
			$class_l = $guest;
			$class_s = $guest;		
			$class_r = $guest;
			$class_d = "class='btn-login' data-title='Sign In'";
			$nonce_l = '';
			$nonce_s = '';
		}else{
			$nonce_l = "data-nonce='".wp_create_nonce('zt-list-noce')."'";
			$nonce_s = "data-nonce='".wp_create_nonce('zt-view-noce')."'";
			$class_l = ($added_l == true) ? 'btn-list added' : 'btn-list';
			$class_s = ($added_s == true) ? 'btn-seen added' : 'btn-seen';	
			$class_r = 'btn-report';
			$class_d = "href='#links' class='btn-links'";
		}		
		
		$process_l = (empty($guest)) ? "id='add-to-list'" : null;	
		$process_s = (empty($guest)) ? "id='add-to-seen'" : null;	
				
		$tooltip_l = ( $class_l  == 'btn-list' || $guest) ? __z('Add to List') : __z('Remove of List');
		$tooltip_s = ( $class_s  == 'btn-seen' || $guest) ? __z('Mark as Seen') : __z('Remove of Seen');
		
		if(empty(['watch'])){
			$trailer = ($type == "tv" || $type == "mv") ? "<a class='btn-trailer'><span>".__z("Trailer")."</span></a>" : "";
		}
		$links = (zeta_here_links($id)) ? "<a ".$class_d." title='".__z("Downloads")."'><span><i class='fas download'></i></span></a>" : "";

		$seen = ($type != 'ep') ? "<a ".$process_s." class='".$class_s."'  data-itemid='".$id."' ".$nonce_s." title='".$tooltip_s."'><span><i class='fas seen'></i></span></a>" : null;
		$out = "<div class='player-qbtn qbtn-".$type.$qbtn_novid."'>";
		$out .= "<div class='btn-left'>";
		$out .= isset($trailer);

		$out .= ($type != 'ep') ? "<a ".$process_l." class='".$class_l."' data-itemid='".$id."' ".$nonce_l." title='".$tooltip_l."'><span><i class='fas list'></i></span></a>" : null;
		//$out .= "<a class='btn-like'><span><i class='far fa-thumbs-up'></i></span></a>";
		$out .= $seen;
		$out .= $links;
		$out .= "<a href='#comments' class='btn-comment' title='".__z("Comments")."'><span><i class='fas comment'></i></span></a>";
		$out .= "</div>";
		$out .= "<div class='btn-sep'></div>";
		if($report == true){
			$out .= "<div class='btn-right'>";
			$out .= "<a class='btn-report' title='".__z("Report Issue")."' data-title='".__z("Report Issue")."'><span><i class='fas report'></i></span></a>";
			$out .= "</div>";
		}
		$out .= "</div>";
	}
	return $out;
}
	
	
//add_action( 'template_redirect', 'account_subpage_redirect' );
function account_subpage_redirect() {
	if(is_page()){
		$account = zeta_get_option('pageaccount');
		$display = zeta_get_option('pageaccount_display');
		if($display !== 'multi' && !is_page($account) && $post->post_parent == $acount) {
			wp_redirect( get_permalink($account) );
			exit();
		}
	}
}


function new_author_base() {
    global $wp_rewrite;
    $author_slug = get_option('zt_author_slug');
    $wp_rewrite->author_base = $author_slug;
}
add_action('init', 'new_author_base');

function check_sidebar($sidebaron = '', $home = '', $archive = '', $single = array(), $page = '', $posttype = ''){
	$sidebar = 'no-sidebar';	
	
	$blog = (is_tag() || is_category()) ? true : null;
	if($sidebaron == true) {	
		if($blog === true){
			if(!empty(zeta_is_true('sidebar_location','blog_archive'))){
				$sidebar = 'sidebar';
			}
			$sidebar = 'sidebar';
		}elseif($home){
			if(zeta_is_true('sidebar_location','home') == true){
				$sidebar = 'sidebar';
			}
		}elseif($archive == true){		
				if(zeta_is_true('sidebar_location', 'archive') == true){
					$sidebar = 'sidebar';
				}
		}elseif($single[0] == true){
				if(zeta_is_true('sidebar_location', $single[1]) == true){
					$sidebar = 'sidebar';
				}
		}elseif($page == true){
				 if(is_page('blog') && zeta_is_true('sidebar_location','blog_archive') == true){
					 $sidebar = 'sidebar';
				}
		}
	}
	return $sidebar;
}


function check_current_page(){
	if(is_front_page() || is_home()) {
		$home = true;
	}elseif(is_page()){
		$page1 = zeta_get_option('pageratings');
		$page2 = zeta_get_option('pagetopimdb');
		$page3 = zeta_get_option('pagetoprated');
		if(is_page(array($page1,$page2,$page3))){
			$archive = true;
		}
		$page = true;
	}elseif(is_archive()){
		$page = true;
		if(is_post_type_archive(array('movies','tvshows','seasons','episode'))){
			$archive = true;
		}
	}elseif(is_single()){
		$single = true;
	}
	$current = array('home' => $home, 'single' => $single, 'archive' => $archive, 'page' => $page);
	return $current;
}


function compose_page_heading($page = array(), $blog = ''){
	
	$heading = (isset($page[0]) == true && is_numeric($page[1])) ? get_post_meta($page[1], 'page_imgheading', true) : null;
	$heading = (!$heading) ? zeta_get_option('page_heading', 'image') : $heading;
	$blog_heading = zeta_get_option('blog_heading', 'image');
	$page = (isset($page[0]) == true && isset($heading) == 'image')  ? true : false;	
	$blog = (isset($blog[0]) == true && isset($blog_heading) == 'image') ? true : false;
	
	if($page == true OR $blog == true){
		$postid = get_the_id();
		$cats = get_the_terms(get_the_id(),'category');
		$default = ZETA_URI.'/assets/img/no/zt_backdrop.png';
		$thumb = (has_post_thumbnail($postid) && $img = wp_get_attachment_image_src( get_post_thumbnail_id($postid))) ? $img[0] : $default;
		$thumb = "style='background-image:url({$thumb})'";

		$header = '<div class="head-content blog"><div class="wrapper"><div class="blog-heading">';
		$header .= '<h3>'.get_the_title().'</h3>';
		if($cats && is_array($cats)){
			$cat_slug = get_option('category_base');
			$cat_slug = ($cat_slug) ? home_url().'/'.$cat_slug.'/' : home_url().'/category/';
			if(has_category()){
				$header .= '<div class="blog-cat">';
				$header .= '<ul class="cat-list">';
					foreach($cats as $cat){
					$header .= '<li><a href="'.$cat_slug.$cat->name.'">'.$cat->name.'</a></li>';
					}
				$header .= '</ul>';
				$header .= '</div>';
			}
		}
		$header .= '</div></div><div class="blog-headbg" '.$thumb.'></div></div>';				
	}	
	
	return isset($header);
	
}


function zeta_transient_getposts($transient_name = '', $args = '', $duration = ''){
	if($transient_name && $duration && $args){
		$data = get_transient( $transient_name  );
		if ( false === $data ) {
			$data = get_posts($args);
			set_transient( $transient_name, $data, $duration );
		}
	}
	return $data;
	
}


# Main required ( Important )
require get_parent_theme_file_path('/inc/core/zetathemes/init.php');
# Codestar Framework
require get_parent_theme_file_path('/inc/core/codestar/classes/setup.class.php');
require get_parent_theme_file_path('/inc/zeta_options.php');
# Main requires
require get_parent_theme_file_path('/inc/zeta_scripts.php');
require get_parent_theme_file_path('/inc/zeta_views.php');
require get_parent_theme_file_path('/inc/zeta_cache.php');
require get_parent_theme_file_path('/inc/zeta_player.php');
require get_parent_theme_file_path('/inc/zeta_links.php');
require get_parent_theme_file_path('/inc/zeta_comments.php');
require get_parent_theme_file_path('/inc/zeta_collection.php');
require get_parent_theme_file_path('/inc/zeta_customizer.php');
require get_parent_theme_file_path('/inc/zeta_minify.php');
require get_parent_theme_file_path('/inc/zeta_ajax.php');
require get_parent_theme_file_path('/inc/zeta_notices.php');
require get_parent_theme_file_path('/inc/zeta_metafields.php');
require get_parent_theme_file_path('/inc/zeta_metadata.php');
require get_parent_theme_file_path('/inc/zeta_database.php');
require get_parent_theme_file_path('/inc/zeta_ads.php');
require get_parent_theme_file_path('/inc/zeta_auth.php');
require get_parent_theme_file_path('/inc/zeta_lazyload.php');
require get_parent_theme_file_path('/inc/zeta_extras.php');
# Google Drive
require get_parent_theme_file_path('/inc/gdrive/class.gdrive.php');
# More functions
require get_parent_theme_file_path('/inc/includes/rating/init.php');
require get_parent_theme_file_path('/inc/includes/metabox.php');
require get_parent_theme_file_path('/inc/includes/slugs.php');
require get_parent_theme_file_path('/inc/widgets/widgets.php');
