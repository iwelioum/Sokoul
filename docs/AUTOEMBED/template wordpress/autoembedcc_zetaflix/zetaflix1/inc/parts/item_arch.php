<?php
/*
* -------------------------------------------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @aopyright: (c) 2023 Zetathemes. All rights reserved
* -------------------------------------------------------------------------------------
*
* @since 1.0.0
*
*/


$posttype = $post->post_type;
$postslug = $post->post_name;

switch($posttype) {

    case 'movies':
        $postmeta = zeta_postmeta_movies($post->ID);
		$posterStyle = zeta_get_option('poster_style','horizontal');
		$posterStyle = apply_filters('module_poster_style', $posterStyle);
		$posterSource =  zeta_get_option('poster_source','meta');
		$posterSource = apply_filters('module_poster_source', $posterSource);
		
        break;
    case 'tvshows':
        $postmeta = zeta_postmeta_tvshows($post->ID);
		$posterStyle = zeta_get_option('poster_style','horizontal');
		$posterStyle = apply_filters('module_poster_style', $posterStyle);
		$posterSource =  zeta_get_option('poster_source','meta');
		$posterSource = apply_filters('module_poster_source', $posterSource);
        break;
    case 'seasons':
        $postmeta = zeta_postmeta_seasons($post->ID);
		$posterStyle = zeta_get_option('poster_style_ss','vertical');
		$posterStyle = apply_filters('module_poster_style_ss', $posterStyle);
		$posterSource =  zeta_get_option('poster_source_ss','thumb');
		$posterSource = apply_filters('module_poster_source_ss', $posterSource);
		
        break;
    case 'episodes':
        $postmeta = zeta_postmeta_episodes($post->ID);
		$posterStyle = zeta_get_option('poster_style_ep','horizontal');
		$posterStyle = apply_filters('module_poster_style_ep', $posterStyle);
		$posterSource =  zeta_get_option('poster_source_ep','meta');
		$posterSource = apply_filters('module_poster_source_ep', $posterSource);		
        break;
}

$data_qual = zeta_is_true('poster_data','quality');
$quality = ($data_qual) ? get_the_term_list( $post->ID, 'ztquality') : null;
$data_titl = zeta_is_true('poster_data','title');


$urating = zeta_isset(isset($postmeta), '_starstruck_avg');
$imdbrat = zeta_isset(isset($postmeta), 'imdbRating');
$release = zeta_isset(isset($postmeta), 'release_date');
$airdate = zeta_isset(isset($postmeta), 'first_air_date');
$viewsco = zeta_isset(isset($postmeta), 'zt_views_count');
$runtime = zeta_isset(isset($postmeta), 'runtime');
$maxwidth = zeta_get_option('max_width','1200');
$playicon = zeta_get_option('play_icon','play1');
$imdbrat = ($imdbrat) ? $imdbrat : '0';
$poster_meta = ($posterSource == 'meta') ? 'zt_backdrop' : 'zt_poster';
$poster = omegadb_get_poster($posttype, $post->ID, '', $poster_meta);

// End PHP
?>
<div id="item-<?php echo $post->ID;?>" class="display-item">
	<div class="item-box">
	      <a href="<?php the_permalink();?>" data-url="<?php echo $postslug;?>" data-ptype="<?php echo $posttype;?>" title="<?php the_title(); ?>"></a>
	   <div class="item-desc-hover <?php echo $playicon;?>">
		  <?php zt_useritem_btn($post->ID, $posttype, $post->post_name);?>
	   </div>

	   
	   <img data-original="<?php echo $poster; ?>" class="thumb mli-thumb" alt="<?php the_title(); ?>" src="<?php echo $poster; ?>">
	   
	      <div class="item-desc">
	<?php if($data_titl){?>
	  <div class="item-desc-title">
		<h3><?php the_title();?></h3>
	  </div>
	<?php }?>
	
	<?php if($data_qual){?>
      <div class="item-desc-hl">
         <div class="desc-hl-right">
			<?php echo ($quality) ? '<span class="item-quality">'.strip_tags($quality).'</span>' : null; ?>
         </div>
      </div>
	<?php }?>
   </div>
   </div>
</div>