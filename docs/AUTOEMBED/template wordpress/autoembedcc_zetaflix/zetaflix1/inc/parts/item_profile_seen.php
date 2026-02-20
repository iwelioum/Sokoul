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

$postauthor = $post->post_author;
$posttype = $post->post_type;
$postslug = $post->post_name;
$posterStyle = zeta_get_option('poster_style','horizontal');
$posterStyle = apply_filters('module_poster_style', $posterStyle);
$posterSource =  zeta_get_option('poster_source','meta');
$posterSource = apply_filters('module_poster_source', $posterSource);
$poster = ($posterStyle == 'Vertical' || $posterStyle == 'vertical') ? omegadb_get_poster('' , $post->ID) : omegadb_get_poster('', $post->ID, '', 'zt_backdrop', 'w300', $posterSource);
$years = strip_tags(get_the_term_list($post->ID, 'ztyear'));


?>
<div id="item-<?php echo $post->ID;?>" class="display-item">
<div class="item-box">
                  <div class="item-desc-hover">
                    		  <?php zt_useritem_btn($post->ID, $posttype, $post->post_name, 'profile-seen', $postauthor);?>
                  </div>
                  <div class="item-data">
                    <h3><?php the_title(); ?></h3>
		<?php if(zeta_is_true('poster_data','year')){?>		
		<span class="item-date"><?php echo $years;?></span>
		<?php }?>
                  </div>
                  <img data-original="<?php echo $poster; ?>" class="thumb mli-thumb" alt="<?php the_title(); ?>" src="<?php echo $poster; ?>">
                </div>
				</div>