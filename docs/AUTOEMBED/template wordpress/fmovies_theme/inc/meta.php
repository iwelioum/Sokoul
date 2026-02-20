<?php
/**
 * Custom Post metadata functions
 *
 * @package fmovie
 */

// Quality
function Qualita() {
    global $post;
    $quality = get_the_term_list($post -> ID, 'quality', '', ', ');
    if (!empty($quality)) echo '<span class="quality">', strip_tags($quality) , '</span>';
    } 
    
// Qua
function Qua() {
    global $post;
    $quality = get_the_term_list($post -> ID, 'quality', '', ', ');
    if (!empty($quality)) echo '<div class="quality">', strip_tags($quality) , '</div>';
    } 

// Durata
function Durata() {
    global $post;
    //$runtime = esc_html(get_post_meta($post -> ID, 'runtime', true));
    $runtime = esc_html(get_post_meta($post -> ID, 'Runtime', true));
    //echo ($runtime != '' ? '<span>' . $runtime . '</span>' : '');
    if ($runtime == ' min') {
        echo '<span>n/a</span>';
        } else {
        echo '<span>' . $runtime . '</span>';
        } 
    } 
    
// ura
function Dura() {
    global $post;
    //$runtime = esc_html(get_post_meta($post -> ID, 'runtime', true));
    $runtime = esc_html(get_post_meta($post -> ID, 'Runtime', true));
    //echo ($runtime != '' .  . '' ? '' . $runtime . '' : '');
    if ($runtime == ' min') {
        echo 'n/a';
        } else {
        echo $runtime;
        } 
    
    } 

// Mpa
function Mpa() {
    global $post;
    $mpa = esc_html(get_post_meta($post -> ID, 'Rated', true));
    echo ($mpa != '' ? '' . $mpa . '' : '');
    } 

// Years
function Years() {
    global $post;
    $years = get_the_term_list($post -> ID, 'years');
    $years = strip_tags($years);
    if (taxonomy_exists('years')) {
        echo $years;
        } 
    } 

// Average
function Average() {
    global $post;
    //$vote_average = esc_html(get_post_meta($post -> ID, 'vote_average', true));
    $vote_average = esc_html(get_post_meta($post -> ID, 'vote_average', true));
    $vote_average = substr($vote_average, 0, 3);
    if ($vote_average != '') {
        echo $vote_average;
        } 
    } 

// VoteCount
function VoteCount() {
    global $post;
    $vote_count = esc_html(get_post_meta($post -> ID, 'vote_count', true));
    //$vote_count = '232313';
    if ($vote_count != '') {
        echo $vote_count;
        } 
    } 

//tmdb_id
function tmdb_id() {
    global $post;
    $tmdb_id = esc_html(get_post_meta($post -> ID, 'id', true));
    if ($tmdb_id != '') {
        echo $tmdb_id;
        } 
    } 

//imdb_id
function imdb_id() {
    global $post;
    $imdb_id = esc_html(get_post_meta($post -> ID, 'imdb_id', true));
    if ($imdb_id != '') {
        echo $imdb_id;
        } 
    } 
// Poster
function Poster() {
    global $post;
    $featured_img_url = get_the_post_thumbnail_url(get_the_ID(), 'full');
    $poster_path = esc_html(get_post_meta($post -> ID, 'poster_path', true));
    //$poster_path = str_replace("w500", "w600_and_h900_bestv2", $poster_path);
    
    if ($poster_path == '') {
        echo esc_url($featured_img_url);
        } else {
        echo esc_url('https://image.tmdb.org/t/p/w600_and_h900_bestv2'.$poster_path);
        } 
    } 
    
// SinglePoster
function SinglePoster() {
	global $post;
    $missing = esc_url( get_template_directory_uri() ).'/assets/img/noimage.webp';
	$featured_img_url = get_the_post_thumbnail_url(get_the_ID(), 'full');
	$poster_path = esc_html(get_post_meta($post -> ID, 'poster_path', true));
	$poster500 = 'https://image.tmdb.org/t/p/w600_and_h900_bestv2' . $poster_path;
    if ($poster500 == 'https://image.tmdb.org/t/p/w600_and_h900_bestv2') {
    $poster500 = $missing;
    }else if ($poster500 == 'https://image.tmdb.org/t/p/w600_and_h900_bestv2null') {
	$poster500 = $missing;	
    }
	if ($poster_path == '') {
		if ( has_post_thumbnail() ) {
		$singleposter = esc_url($featured_img_url);
		} else {
		$singleposter = $missing;
		}
		} else {
		$singleposter = esc_url($poster500);
		}
        if ( has_post_thumbnail() ) {
		echo esc_url($featured_img_url);
		}else {
		echo esc_url($poster500);
		}
} 
    

// Tagline
function Tagline() {
    global $post;
    $tagline = esc_html(get_post_meta($post -> ID, 'tagline', true));
    echo ($tagline != '' ? '' . $tagline . '' : '');
    } 

// Overview
function Overview() {
    global $post;
    $overview = esc_html(get_post_meta($post -> ID, 'overview', true));
    echo ($overview != '' ? '' . $overview . '' : '');
    } 

// SingleGenre
function SingleGenre() {
	global $post;
	$category = get_the_category();
	if (!empty($category[0]))
	if ( $category[0] ) { 
	echo '<a href="' . get_category_link( $category[0]->term_id ) . '">' . $category[0]->cat_name . '</a>';
	   }
	if (!empty($category[1]))
	if ( $category[1] ) { 
	echo '<a href="' . get_category_link( $category[1]->term_id ) . '">' . $category[1]->cat_name . '</a>';
	   }
	}

// Produzione
function Produzione() {
    global $post;
    $production_companies = esc_html(get_post_meta($post -> ID, 'production_companies', true));
    $production_companies = str_replace(",", "<br>", $production_companies);
     echo ($production_companies != '' ? '' . $production_companies . '' : '');
    } 

// Edit
function Edit() {
    global $post;
    edit_post_link('edit', '<li>', '</li>');
    } 

// Backdrop
function Backdrop() {
    global $post;
    $cover = esc_html(get_post_meta($post -> ID, 'cover', true));
    $backdrop_path = esc_html(get_post_meta($post -> ID, 'backdrop_path', true));
    
    //if ($backdrop_path == '') {
    if ($cover != '') {
       echo wp_get_attachment_image_url($cover, 'cover');
        } else {
        //echo esc_url($backdrop_path);
        echo esc_url('//image.tmdb.org/t/p/original'.$backdrop_path);
        } 
    } 
    
// DataSrc
function DataSrc() {
    global $post;
    $backdrop_path = esc_html(get_post_meta($post -> ID, 'backdrop_path', true));
    
    
    if ($backdrop_path == '') {
        echo '';
        } else {
        //echo esc_url($backdrop_path);
        echo esc_url('//image.tmdb.org/t/p/original'.$backdrop_path);
        } 
    } 

// DataSrcMobile
function DataSrcMobile() {
    global $post;
    $backdrop_path = esc_html(get_post_meta($post -> ID, 'backdrop_path', true));
    $srcmobile = str_replace('original', 'w780', $backdrop_path);
    
    if ($backdrop_path == '') {
        echo '';
        } else {
        //echo esc_url($srcmobile);
        echo esc_url('//image.tmdb.org/t/p/w780'.$backdrop_path);
        } 
    } 

// DataThumb
function DataThumb() {
    global $post;
    $backdrop_path = esc_html(get_post_meta($post -> ID, 'backdrop_path', true));
    //$datathumb = str_replace('original', 'w300', $backdrop_path);
    
    if ($backdrop_path == '') {
        echo '';
        } else {
        //echo esc_url($datathumb);
         echo esc_url('//image.tmdb.org/t/p/w300'.$backdrop_path);
        } 
    } 

//tooltipGenreList
function tooltipGenreList() {
	global $post;
	$category = get_the_category();
	if (!empty($category[0]))
	if ( $category[0] ) { 
	echo '<a href="' . get_category_link( $category[0]->term_id ) . '">' . $category[0]->cat_name . '</a>';
	   }
    if (!empty($category[1]))
	if ( $category[1] ) { 
	echo ', <a href="' . get_category_link( $category[1]->term_id ) . '">' . $category[1]->cat_name . '</a>';
	   }
	}

//SingleGenres
function SingleGenres() {
	global $post;
    if( false != get_the_term_list( $post->ID, 'category' ) ) {
        echo '<div><span>' . genre . ':</span> <span>'.get_the_term_list( $post->ID, 'category', '', ', ' ).'</span></div>';
    } 
}

//SingleActors
function SingleActors() {
	global $post;
    if( false != get_the_term_list( $post->ID, 'actors' ) ) {
        echo '<div class="casts"><span>' . stars . ':</span> <span>'.get_the_term_list( $post->ID, 'actors', '', ', ' ).'</span></div>';
    } 
}

//SingleYear
function SingleYear() {
	global $post;
    if( false != get_the_term_list( $post->ID, 'years' ) ) {
        echo '<div><span>' . year . ':</span><span>'.get_the_term_list( $post->ID, 'years', '', ', ' ).'</span></div>';
    } 
}
//tooltipCountryList
function tooltipCountryList() {
	global $post;
     if (taxonomy_exists('country')) {
         echo get_the_term_list( $post->ID, 'country', '', ', ' );
        } 
   
	}

//SingleCountry
function SingleCountry() {
	global $post;
    if( false != get_the_term_list( $post->ID, 'country' ) ) {
        echo '<div><span>' . country . ':</span> <span>'.get_the_term_list( $post->ID, 'country', '', ', ' ).'</span></div>';
        } 
   
	}



//Cast
function Cast() {
	global $post;
     if (taxonomy_exists('actors')) {
         echo get_the_term_list( $post->ID, 'actors', '', ', ' );
        } 
   
	} 
//Keywords
function Keywords() {
    global $post;
    if (has_tag()) {
        the_tags('<div class="tags"> <span>Tags:</span> <span>', ', ', '</span> </div>');
        } else {
        // Article untagged
    } 
    } 

//Regista & Creator
function Regista() {
	global $post;
    if ( is_post_template( 'tv.php' ) ) {
     if (taxonomy_exists('creator')) {
         echo '<div><span>' . creator . ':</span><span>'.get_the_term_list( $post->ID, 'creator', '', ', ' ).'</span></div>';
         //echo get_the_term_list( $post->ID, 'creator', '', ', ' );
        } 
    } else {
     if (taxonomy_exists('director')) {
         echo '<div><span>' . director . ':</span><span>'.get_the_term_list( $post->ID, 'director', '', ', ' ).'</span></div>';
         //echo get_the_term_list( $post->ID, 'director', '', ', ' );
         
        } 
   } 
	} 
    
//Tipo
function Tipo() {
	global $post;
    if ( in_category('TV Series') ) {
echo 'TV';
    } else {
echo 'Movie';
   } 
	} 

//Breadcrumb
function BreadcrumbType() {
	global $post;
	if ( in_category('tv-series') ) { 
	echo tvseries;
		} else {
	echo txtmovies;
		}
	}
// release_date
function release_date() {
    global $post;
    $release_date = esc_html(get_post_meta($post -> ID, 'release_date', true));
    if ($release_date != '') {
        echo '<div><span>'.year.':</span> <span itemprop="dateCreated">'.$release_date.'</span></div>';
        } 
    } 
    
// release_date
function ReportRelease_date() {
    global $post;
    $release_date = esc_html(get_post_meta($post -> ID, 'release_date', true));
    if ($release_date != '') {
        echo ' ('.$release_date.')';
        } 
    } 
    

// SearchDate
function SearchDate() {
    global $post;
    $release_date = esc_html(get_post_meta($post -> ID, 'release_date', true));
    $search_date = substr($release_date, 0, 4);
    if ($release_date != '') {
        echo '<i class="dot"></i>'.$search_date;
        } 
    } 
// Favorite
function Favorite() {
	global $post;
	$movie_ID = get_the_ID();
	echo "<span class='bookmark inactive' data-bookmark='Favorite' id='".$movie_ID."'>Favorite</span>";
}

// FavoriteItem
function FavoriteItem() {
	global $post;
	$movie_ID = get_the_ID();
	echo "<a href='#' class='bookmark inactive' data-bookmark='Favorite' id='".$movie_ID."'>Favorite</a>";
}

//MenuGenre
function MenuGenre() {
$slug = 'Recommended';
$cat = get_category_by_slug($slug); 
$catID = $cat->term_id;
$categories = get_categories( [
'taxonomy' => 'category',
'type' => 'post',
'child_of' => 0,
'parent' => '',
'orderby'=> 'name',
'order'=> 'ASC',
'hide_empty'   => 1,
'hierarchical' => 1,
'exclude'  => array(1, 2, 3, $catID),
'include'=> '',
'number' => 0,
'pad_counts'   => false,
] );
if( $categories ) {
foreach( $categories as $cat ) {
?>
<li><a href="<?php echo get_category_link($cat->term_id); ?>"><?php echo $cat->name; ?></a></li>
<?php
}
}
}

//MenuCountry
function MenuCountry() {
$terms = get_terms( array(
  'taxonomy' => 'country',
  'hide_empty' => true,
  'order' => 'DESC',
) );

if (!empty( $terms ) && ! is_wp_error($terms)){
  foreach ( $terms as $term ) {
    $class = ( is_tax('country', $term->slug) ) ? ' class="active"' : '';
    echo '<li><a'.$class.' href="' . get_term_link( $term ) .'">' . $term->name . '</a></li>
';  }
}
}

//NavCountry
function NavCountry() {
$terms = get_terms( array(
  'taxonomy' => 'country',
  'hide_empty' => true,
  'order' => 'DESC',
) );

if (!empty( $terms ) && ! is_wp_error($terms)){
  foreach ( $terms as $term ) {
    $class = ( is_tax('country', $term->slug) ) ? ' class="tax-item tax-item-' . $term->term_id . ' current-tax"' : ' class="tax-item tax-item-' . $term->term_id . '"';
    echo '<li'.$class.'>';
    echo '<a href="' . get_term_link( $term ) .'" title="' . $term->name . '">' . $term->name . '</a>
    ';
    echo '</li>
    ';  
    }
  }
}


//MenuYear
function MenuYear() {
$terms = get_terms( array(
  'taxonomy' => 'years',
  'hide_empty' => true,
  'order' => 'DESC',
) );

if (!empty( $terms ) && ! is_wp_error($terms)){
  foreach ( $terms as $term ) {
    $class = ( is_tax('years', $term->slug) ) ? 'active' : '';
    echo '<li><a class="'.$class.'" href="' . get_term_link( $term ) .'">' . $term->name . '</a></li>
';  }
  }
}

//Genre
function Genre() {
$slug = 'Recommended';
$cat = get_category_by_slug($slug); 
$catID = $cat->term_id;
$categories = get_categories( [
'taxonomy' => 'category',
'type' => 'post',
'child_of' => 0,
'parent' => '',
'orderby'=> 'name',
'order'=> 'ASC',
'hide_empty'   => 1,
'hierarchical' => 1,
'exclude'  => array(1, 2, 3, $catID),
'include'=> '',
'number' => 0,
'pad_counts'   => false,
] );
if( $categories ) {
foreach( $categories as $cat ) {
?>
<li><a href="<?php echo get_category_link($cat->term_id); ?>"><?php echo $cat->name; ?></a></li>
<?php
}
}
}

//DropdownYears
function DropdownYears() {
$terms = get_terms( array(
  'taxonomy' => 'years',
  'hide_empty' => true,
  'order' => 'DESC',
) );

if (!empty( $terms ) && ! is_wp_error($terms)){
  foreach ( $terms as $term ) {
    $class = ( is_tax('years', $term->slug) ) ? ' active' : '';
    echo '<li><a class="dropdown-item'.$class.'" href="' . get_term_link( $term ) .'">' . $term->name . '</a></li>
';  }
  }
}

//DropdownType
function DropdownType() {
// cat movies
$category_movies = get_cat_ID( 'Movies' ); 
$category_link_movies = get_category_link( $category_movies );
// cat series
$category_id = get_cat_ID( 'TV Series' ); 
$category_link = get_category_link( $category_id );
//classes
$class_movies = ( is_category('movies') ) ? ' active' : '';
$class_tv = ( is_category('tv-series') ) ? ' active' : '';
echo '<li><a class="dropdown-item'.$class_movies.'" href="' . esc_url( $category_link_movies ) .'">' . txtmovies . '</a></li>';
echo '<li><a class="dropdown-item'.$class_tv.'" href="' . esc_url( $category_link ) .'">' . tvseries . '</a></li>';
}

//DropdownCat
function DropdownCat() {
$slug = 'Recommended';
$cat = get_category_by_slug($slug); 
$catID = $cat->term_id;
$terms = get_terms( array(
  'taxonomy' => 'category',
  'hide_empty' => true,
  'exclude'  => array(1, 2, 3, $catID),
  'orderby'=> 'name',
  'order'=> 'ASC',
  ) );

if (!empty( $terms ) && ! is_wp_error($terms)){
  foreach ( $terms as $term ) {
    $class = ( is_category( $term->name ) ) ? 'dropdown-item active' : 'dropdown-item'; // assign this class if we're on the same category page as $term->name
    echo '<li><a href="' . get_term_link( $term ) . '" class="' . $class . '">' . $term->name . '</a></li>';
	}
  }
}

//DropdownCountry
function DropdownCountry() {
$terms = get_terms( array(
  'taxonomy' => 'country',
  'hide_empty' => true,
  'order' => 'DESC',
) );

if (!empty( $terms ) && ! is_wp_error($terms)){
  foreach ( $terms as $term ) {
    $class = ( is_tax('country', $term->slug) ) ? ' active' : '';
    echo '<li><a class="dropdown-item'.$class.'" href="' . get_term_link( $term ) .'">' . $term->name . '</a></li>
';  }
  }
}

//DropdownSource
function DropdownSource() {
$terms = get_terms( array(
  'taxonomy' => 'source',
  'hide_empty' => false,
  'order' => 'DESC',
) );

if (!empty( $terms ) && ! is_wp_error($terms)){
  foreach ( $terms as $term ) {
    $class = ( is_tax('source', $term->slug) ) ? ' class="dropdown-item active"' : ' class="dropdown-item"';
    echo '<li><a'.$class.' href="' . get_term_link( $term ) .'">' . $term->name . '</a></li>
';  }
  }
}

//DropdownQuality
function DropdownQuality() {
$terms = get_terms( array(
  'taxonomy' => 'quality',
  'hide_empty' => false,
  'order' => 'DESC',
) );

if (!empty( $terms ) && ! is_wp_error($terms)){
  foreach ( $terms as $term ) {
    $class = ( is_tax('quality', $term->slug) ) ? ' class="dropdown-item active"' : ' class="dropdown-item"';
    echo '<li><a'.$class.' href="' . get_term_link( $term ) .'">' . $term->name . '</a></li>
';  }
  }
}

//DropdownLanguage
function DropdownLanguage() {
$terms = get_terms( array(
  'taxonomy' => 'language',
  'hide_empty' => false,
  'order' => 'DESC',
) );

if (!empty( $terms ) && ! is_wp_error($terms)){
  foreach ( $terms as $term ) {
    $class = ( is_tax('language', $term->slug) ) ? ' class="dropdown-item active"' : ' class="dropdown-item"';
    echo '<li><a'.$class.' href="' . get_term_link( $term ) .'">' . $term->name . '</a></li>
';  }
  }
}

//orderby_taxonomies
function orderby_tax_clauses( $clauses, $wp_query ) {
    global $wpdb;
    $taxonomies = get_taxonomies();
    foreach ($taxonomies as $taxonomy) {
        if ( isset( $wp_query->query['orderby'] ) && $taxonomy == $wp_query->query['orderby'] ) {
            $clauses['join'] .=<<<SQL
LEFT OUTER JOIN {$wpdb->term_relationships} ON {$wpdb->posts}.ID={$wpdb->term_relationships}.object_id
LEFT OUTER JOIN {$wpdb->term_taxonomy} USING (term_taxonomy_id)
LEFT OUTER JOIN {$wpdb->terms} USING (term_id)
SQL;
            $clauses['where'] .= " AND (taxonomy = '{$taxonomy}' OR taxonomy IS NULL)";
            $clauses['groupby'] = "object_id";
            $clauses['orderby'] = "GROUP_CONCAT({$wpdb->terms}.name ORDER BY name ASC) ";
            $clauses['orderby'] .= ( 'ASC' == strtoupper( $wp_query->get('order') ) ) ? 'ASC' : 'DESC';
        }
    }
    return $clauses;
}

add_filter('posts_clauses', 'orderby_tax_clauses', 10, 2 );

//post views
function getPostViews($postID){
    $count_key = 'post_views_count';
    $count = get_post_meta($postID, $count_key, true);
    if($count==''){
        delete_post_meta($postID, $count_key);
        add_post_meta($postID, $count_key, '0');
        return "0 ";
    }
    return $count.' ';
}
function setPostViews($postID) {
    $count_key = 'post_views_count';
    $count = get_post_meta($postID, $count_key, true);
    if($count==''){
        $count = 0;
        delete_post_meta($postID, $count_key);
        add_post_meta($postID, $count_key, '0');
    }else{
        $count++;
        update_post_meta($postID, $count_key, $count);
    }
}

// Trailer
function Trailer() {
    global $post;
    
    $string = 'emarfi';
    $film = strrev ( $string );
    
    $youtube_id = esc_html(get_post_meta($post -> ID, 'youtube_id', true));
    $youtube_id = str_replace('https://youtu.be/', '', $youtube_id);
    $youtube_id = str_replace('https://www.youtube.com/watch?v=', '', $youtube_id);
    $youtube_id = str_replace('https://www.youtube.com/embed/', '', $youtube_id);
    
    $arr = explode('[',$youtube_id); 
	$youtube_id = implode('',$arr); 
	$arr = explode(']',$youtube_id); 
	$youtube_id = implode('',$arr); 
    
    echo ($youtube_id != '' ? '<!-- modal --><div class="modal fade" id="modal"><span class="close" data-dismiss="modal">&times;</span><div class="modal-dialog modal-dialog-centered"><div class="modal-content shadow"><div class="modal-body rounded p-0"><div class="embed-responsive embed-responsive-16by9 rounded"><' . $film . ' loading="lazy" class="embed-responsive-item" src="//www.youtube.com/embed/' . $youtube_id . '" scrolling="no" frameborder="0" allow="accelerometer;autoplay;encrypted-media;gyroscope;picture-in-picture" allowfullscreen="" webkitallowfullscreen=""></' . $film . '></div></div></div></div></div><!-- #modal -->' : '');
    } 

// TrailerButton
function TrailerButton() {
    global $post;
    $youtube_id = esc_html(get_post_meta($post -> ID, 'youtube_id', true));
    echo ($youtube_id != '' ? '<div class="ctl onoff d-none d-md-block" data-toggle="modal" data-target="#modal"><i class="fab fa-youtube"></i>  ' . trailer . '</div>' : '');
    } 
    
//time ago
function meks_time_ago() {
	return human_time_diff( get_the_time( 'U' ), current_time( 'timestamp' ) );
}



//number_of_seasons
function number_of_seasons() {
    global $post;
    $number_of_seasons = esc_html(get_post_meta($post -> ID, 'number_of_seasons', true));
    $years = get_the_term_list($post -> ID, 'years');
    $years = strip_tags($years);
    if ($number_of_seasons == '') {
    if (taxonomy_exists('years')) {
        echo $years;
    }
        } else {
        echo 'SS '.$number_of_seasons;
        } 
    } 


//last_episode_to_air
function last_episode_to_air() {
    global $post;
    $last_episode_to_air = esc_html(get_post_meta($post -> ID, 'last_episode_to_air', true));
    $number_of_episodes = esc_html(get_post_meta($post -> ID, 'number_of_episodes', true));
    if ($last_episode_to_air == '') {
        echo 'EP '.$number_of_episodes;
        } else {
        echo 'EP '.$last_episode_to_air;
        } 
    } 
  
//urlToDomain
function urlToDomain($url) {
   global $post;
   return implode(array_slice(explode('/', preg_replace('/https?:\/\/(www\.)?/', '', $url)), 0, 1));
}

//Reverse
function Reverse($str){
    global $post;
    return strrev($str);
}
