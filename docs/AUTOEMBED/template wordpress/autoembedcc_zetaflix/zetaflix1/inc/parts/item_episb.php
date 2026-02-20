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

// Compose data
$postmeta = zeta_postmeta_episodes($post->ID);
$posterStyle = zeta_get_option('poster_style_ep','horizontal');
$posterStyle = apply_filters('module_poster_style_ep', $posterStyle);
$posterSource =  zeta_get_option('poster_source_ep','meta');
$posterSource = apply_filters('module_poster_source_ep', $posterSource);
$posterMeta =  zeta_get_option('poster_meta_source_ep','zt_backdrop');
$posterMeta = apply_filters('module_poster_meta_source_ep', $posterMeta);
$poster = omegadb_get_poster($posttype, $post->ID, 'full', $posterMeta, '', $posterSource);
$years = strip_tags(get_the_term_list($post->ID, 'ztyear'));
$qualitys = get_the_terms($post->ID, 'ztquality');
$languages = get_the_terms($post->ID, 'ztlanguage');
$serie = zeta_isset($postmeta,'serie');
$urating = zeta_isset($postmeta, '_starstruck_avg');
$imdbrat = zeta_isset($postmeta, 'imdbRating');
$release = zeta_isset($postmeta, 'release_date');
$airdate = zeta_isset($postmeta, 'first_air_date');
$viewsco = zeta_isset($postmeta, 'zt_views_count');
$runtime = zeta_isset($postmeta, 'runtime');
$season = zeta_isset($postmeta,'temporada');
$episode = zeta_isset($postmeta,'episodio');
$maxwidth = zeta_get_option('max_width','1200');
$playicon = zeta_get_option('play_icon','play1');
$imdbrat = ($imdbrat) ? $imdbrat : '0';


$season = zeta_isset($postmeta,'temporada');
$episode = zeta_isset($postmeta,'episodio');


$episode_desc = (!empty($season) && !empty($episode)) ? '<span class="season-desc">S'.$season.' EP'.$episode.'</span>' : null;
$class = (!empty($episode_desc)) ? 'xtras' : null;

// End PHP
?>
<div id="item-<?php echo $post->ID;?>" class="module-item">
<div class="item-loader"></div>
<div class="item-data">
	      <a href="<?php the_permalink();?>" data-url="<?php echo $postslug;?>" data-ptype="<?php echo $posttype;?>" class="<?php echo $playicon;?>" title="<?php the_title(); ?>">
   <img class="thumb mli-thumb" alt="<?php the_title(); ?>" src="<?php echo $poster; ?>" style="display: inline-block;">
   </a>
   <div class="item-desc">
	<?php if(zeta_is_true('poster_data','title') || zeta_is_true('poster_data','year')){?>
	  <div class="item-desc-title <?php echo $class;?>">
	  <?php if(zeta_is_true('poster_data','title')){?>
		<h3><?php echo $serie;?></h3>
		<?php echo $episode_desc;?>
	  <?php }?>
		<?php if(zeta_is_true('poster_data','year')){?>		
		<span class="item-date"><?php echo $years;?></span>
		<?php }?>
	  </div>
	<?php }?>
	
	<?php if(zeta_is_true('poster_data','language') || zeta_is_true('poster_data','quality')){?>
      <div class="item-desc-hl">
         <div class="desc-hl-right">
			<?php 

			if($qualitys){ 
				foreach( $qualitys AS $quality) {
					echo '<span class="item-quality">'.$quality->name.'</span>'; 
				}
			}
			
			?>
			<?php 

			if($languages){ 
				foreach( $languages AS $language) {
					echo '<span class="item-language us">'.$language->name.'</span>'; 
				}
			}
			
			?>

         </div>
      </div>
	<?php }?>
   </div>
   <div class="item-desc-hover">
   <?php zt_useritem_btn($post->ID, $posttype, $post->post_name);?>
   </div>
   </div>
</div>














