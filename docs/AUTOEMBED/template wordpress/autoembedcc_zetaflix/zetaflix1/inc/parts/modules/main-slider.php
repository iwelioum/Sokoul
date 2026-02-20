<?php 
$numitems = zeta_get_option('slideritems', '5');
$filter = zeta_get_option('sliderfilter','slider');
$filterpid = zeta_get_option('sliderpostids');
$filtergid = zeta_get_option('slidergenreids');
$posttypes = zeta_get_option('sliderpostypes', 'all');
$layout = zeta_get_option('sliderlayout', 'fulls');
$oderby = zeta_get_option('slidermodorderby','modified');
$oder = zeta_get_option('sliderodorder','DESC');
$fullwidth = zeta_get_option('full_width');
$posttype =  ($posttypes == 'all') ? array('movies','tvshows') : $posttypes;
if($layout == 'fulls' || isset($fullwidth) == true) $fullscreen = true;
$layout = ($fullwidth == true) ? null : $layout;
$layout = apply_filters('site_slider_layout', $layout);
$guest = 'clicklogin';
$nonce = wp_create_nonce('zt-list-noce');
$process_l = (is_user_logged_in()) ? 'slide-to-list' : $guest;			

	


if($filter == 'postid' && !empty($filterpid)){
	$query = array(
		'post_type'		=> $posttype,
		'posts_per_page'=> $numitems,
		'post__in'      => array($filterpid),
		'order'			=> $oder,
		'orderby'		=> $oderby
	);
}elseif($filter == 'genreid' && !empty($filtergid)){
	$query = array(
		'post_type'		=> $posttype,
		'posts_per_page'=> $numitems,
		'post__in'      => array($filtergid),
		'order'			=> $oder,
		'orderby'		=> $oderby
	);
}elseif($filter == 'featured'){
	$query = array(
		'post_type'		=> $posttype,
		'posts_per_page'=> $numitems,
		'meta_key'		=> 'zt_featured_post',
		'meta_value'	=> '1',
		'order'			=> $oder,
		'orderby'		=> $oderby
	);
}else{
	$query = array(
		'post_type'		=> $posttype,
		'posts_per_page'=> $numitems,
		'meta_key'		=> 'zt_featured_slider',
		'meta_value'	=> '1',
		'order'			=> $oder,
		'orderby'		=> $oderby
	);
}


$featured = new WP_Query($query);
if ($featured->have_posts()) { ?>
  
  <div class="featured-slider <?php echo $filterpid;?> <?php echo $layout;?>">
    <div id="main-slider" class="owl-carousel owl-theme">

<?php while($featured->have_posts()) {
	$featured->the_post();
	
	$heading = omegadb_get_slide_logo($post->ID);
	$backdrop = omegadb_get_slide_backdrop($post->ID, 'w1280');
	$imdb		= ( $a = zeta_get_postmeta('imdbRating')) ? $a : 'n/a';
	$theYear	= ($mostrar = $terms = strip_tags( $terms = get_the_term_list( $post->ID, 'ztyear') ) ) ? '<span class="f-year">'.$mostrar.'</span>' : NULL;
	$theQuality	= ($mostrar = $terms = strip_tags( $terms = get_the_term_list( $post->ID, 'ztquality') ) ) ? '<span class="f-quality">'.$mostrar.'</span>' : NULL;
	$theGenre = ($mostrar = get_the_term_list( $post->ID, 'genres', '<div class="f-info2"><span class="f-genre">', ', ', '</span></div>' ) ) ? $mostrar : NULL;
	
	
					$tooltip_l = ( zt_already_listed( $post->ID ) ) ? __z('Remove of List') : __z('Add to List');
				$class_l = (zt_already_listed( $post->ID )) ? 'added' : null;
	?>
	
      <div class="item" id="slide-<?php echo $post->ID;?>">
	  <div class="item-mask"></div>
        <div class="featured-data" <?php echo 'style="background-image: url('.$backdrop.');"';?>>
          <div class="wrapper">
            <div class="f-content">
              <div class="f-title">
                  <?php echo $heading;?>
              </div>
              <div class="f-info">
                <?php echo $theQuality;?>
                <span class="f-imdb"><?php _z('IMDb')?>: <?php echo $imdb;?></span>
                <?php echo $theYear;?>
              </div>
              <?php echo $theGenre;?>
              <div class="f-desc">
               <?php zt_content_alt('120'); ?>
              </div>
              <div class="f-btn">
                <a href="<?php the_permalink();?>"><span class="btnBtn"><i class="fa-solid fa-play"></i> <?php _z('Watch');?></span></a>

				<a data-itemid="<?php echo get_the_id();?>" data-nonce="<?php echo $nonce;?>" data-itemtype="<?php echo $post->post_type;?>" data-itemname="<?php echo $post->post_name;?>" class="<?php echo $process_l;?> <?php echo $class_l;?>" title="<?php echo $tooltip_l;?>"><span class="btnBtn user"><i class="fa-solid fa-plus"></i></span></a>
              </div>
            </div>
          </div>
          <div class="f-side-shade"></div>
          <div class="f-btm-shade"></div>

        </div>
      </div>
<?php } ?>
    </div>
  </div>
  
<?php 
  }

wp_reset_query();?>