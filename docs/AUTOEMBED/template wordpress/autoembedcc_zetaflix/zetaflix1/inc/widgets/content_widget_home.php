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

class ZT_Widget_home extends WP_Widget {

	function __construct() {
		$widget_ops = array('classname' => 'zetathemes_widget', 'description' => __z('Sort content by genres') );
		$control_ops = array('width' => 300, 'height' => 350, 'id_base' => 'ztw_content_genres');
		parent::__construct('ztw_content_genres', __z('ZetaFlix - Stream Genre Module'), $widget_ops, $control_ops );
	}

	public function widget( $args, $instance ) {
		extract( $args );
		//Our variables from the widget settings.
		$zeta_genre  = get_option('zt_genre_slug','genre');
		$title      = apply_filters('widget_title', zeta_isset($instance,'title')) ;
		$num        = zeta_isset($instance,'zt_nun');
		$tipo       = zeta_isset($instance,'zt_tipo');
		$dspl       = (zeta_isset($instance,'zt_style')) ? $instance['zt_style'] : 'slider';
		$genre      = zeta_isset($instance,'zt_genre');
		$orderby       = zeta_isset($instance,'zt_orderby');
		$order       = zeta_isset($instance,'zt_order');
		$plmk		= get_post_type_archive_link('movies');

		?>
		
		<?php if (is_home()) {?>
		<div class="home-module <?php echo $dspl;?> stream movies" data-module-id="featured-movies">
		<div class="module-title <?php echo (!$title) ? 'no-title' : null ;?>">
			<div class="wrapper-box">
				<span><?php echo ($title) ? $title : ucfirst($genre); ?></span>
				<a href="<?php echo get_term_link($genre,'genres'); ?>"><?php _z('View All');?></a>
			</div>
		</div>
		
		<div class="module-content <?php echo ($dspl == 'slider') ? 'owl-carousel' : null;?>">
		<?php 
		$transient = get_transient('zetaflix_home_genres_widget_'.$genre);
		if(false === $transient){
			$transient = new WP_Query( array('genres' => $genre, 'post_type' => $tipo, 'showposts' => $num, 'orderby' => $orderby, 'order' => $order  ) );
			set_transient('zetaflix_home_genres_widget_'.$genre, $transient, MINUTE_IN_SECONDS*5);
		}
		?>

		<?php  while ( $transient->have_posts() ) : $transient->the_post(); 
		

		if($dspl == 'grid'){
			get_template_part('inc/parts/item_arch'); 
		}else{
			get_template_part('inc/parts/item'); 
		}
		
		endwhile;
		wp_reset_query(); ?>
		<?php echo ($dspl != 'slider') ? '<div class="clearfix"></div>' : null;?>
		</div>
		</div>
		
		


<?php	}

	}

	function update( $new_instance, $old_instance ) {
		$instance = $old_instance;
		//Strip tags from title and name to remove HTML
		$instance['title'] = strip_tags( $new_instance['title'] );
		$instance['zt_nun'] = strip_tags( $new_instance['zt_nun'] );
		$instance['zt_tipo'] = strip_tags( $new_instance['zt_tipo'] );
		$instance['zt_style'] = strip_tags( $new_instance['zt_style'] );
		$instance['zt_genre'] = strip_tags( $new_instance['zt_genre'] );
		$instance['zt_rand'] = strip_tags( $new_instance['zt_rand'] );
		$instance['zt_orderby'] = strip_tags( $new_instance['zt_orderby'] );
		$instance['zt_order'] = strip_tags( $new_instance['zt_order'] );
		return $instance;
	}

	function form( $instance ) {
		//Set up some default widget settings.
		$defaults = array('title' => __z('Genre'), 'zt_nun' => '14');
		$instance = wp_parse_args( (array) $instance, $defaults ); ?>
		<p>
			<label for="<?php echo $this->get_field_id('title'); ?>"><?php _z('Title:'); ?></label>
			<input id="<?php echo $this->get_field_id('title'); ?>" name="<?php echo $this->get_field_name('title'); ?>" style="width:100%;" />
		</p>
		<p>
			<label for="<?php echo $this->get_field_id('zt_tipo'); ?>"><?php _z('Content type'); ?></label>
			<select id="<?php echo $this->get_field_id('zt_tipo'); ?>" name="<?php echo $this->get_field_name('zt_tipo'); ?>" style="width:100%;">
				<option <?php if ('' == $instance['zt_tipo'] ) echo 'selected="selected"'; ?> value=""><?php _z('All'); ?></option>
				<option <?php if ('movies' == $instance['zt_tipo'] ) echo 'selected="selected"'; ?> value="movies"><?php _z('Movies'); ?></option>
				<option <?php if ('tvshows' == $instance['zt_tipo'] ) echo 'selected="selected"'; ?> value="tvshows"><?php _z('TV Shows'); ?></option>
            </select>
		</p>
		<p>
			<label for="<?php echo $this->get_field_id('zt_genre'); ?>"><?php _z('Genre'); ?></label>
			<select name="<?php echo $this->get_field_name('zt_genre'); ?>" id="<?php echo $this->get_field_name('zt_genre'); ?>" style="width:100%;">
			<?php $terms = get_terms('genres'); foreach ($terms as $term) { ?>
				<option <?php if ( $term->slug == $instance['zt_genre'] ) echo 'selected="selected"'; ?> value="<?php echo $term->slug; ?>"><?php echo $term->name; ?> (<?php echo $term->count; ?>)</option>
			<?php } ?>
			</select>
		</p>
		<p>
			<label for="<?php echo $this->get_field_id('zt_nun'); ?>"><?php _z('Items number'); ?></label>
			<input type="number" id="<?php echo $this->get_field_id('zt_nun'); ?>" name="<?php echo $this->get_field_name('zt_nun'); ?>" value="<?php echo $instance['zt_nun']; ?>" min="1" max="50" style="width:100%;">
		</p>
		<p>
			<label for="<?php echo $this->get_field_id('zt_style'); ?>"><?php _z('Style'); ?></label>
			<select id="<?php echo $this->get_field_id('zt_style'); ?>" name="<?php echo $this->get_field_name('zt_style'); ?>" style="width:100%;">
				<option <?php if ('grid' != $instance['zt_style'] ) echo 'selected="selected"'; ?> value="slider"><?php _z('Slider'); ?></option>
				<option <?php if ('grid' == $instance['zt_style'] ) echo 'selected="selected"'; ?> value="grid"><?php _z('Grid'); ?></option>
            </select>
		</p>
		<p>
			<label for="<?php echo $this->get_field_id('zt_orderby'); ?>"><?php _z('Order By'); ?></label>
			<select id="<?php echo $this->get_field_id('zt_orderby'); ?>" name="<?php echo $this->get_field_name('zt_orderby'); ?>" style="width:100%;">
				<option <?php if ('date' == $instance['zt_orderby'] ) echo 'selected="selected"'; ?> value="date"><?php _z('Post date'); ?></option>
				<option <?php if ('title' == $instance['zt_orderby'] ) echo 'selected="selected"'; ?> value="title"><?php _z('Post title'); ?></option>
				<option <?php if ('modified' == $instance['zt_orderby'] ) echo 'selected="selected"'; ?> value="modified"><?php _z('Last Modified'); ?></option>
				<option <?php if ('rand' == $instance['zt_orderby'] ) echo 'selected="selected"'; ?> value="rand"><?php _z('Random'); ?></option>
            </select>
		</p>
		<p>
			<label for="<?php echo $this->get_field_id('zt_order'); ?>"><?php _z('Style'); ?></label>
			<select id="<?php echo $this->get_field_id('zt_order'); ?>" name="<?php echo $this->get_field_name('zt_order'); ?>" style="width:100%;">
				<option <?php if ('DESC' == $instance['zt_order'] ) echo 'selected="selected"'; ?> value="DESC"><?php _z('Descending'); ?></option>
				<option <?php if ('ASC' == $instance['zt_order'] ) echo 'selected="selected"'; ?> value="ASC"><?php _z('Ascending'); ?></option>
            </select>
		</p>
		
		<p>
			<input class="checkbox" type="checkbox" <?php checked( $instance[ 'zt_rand' ], 'on'); ?> id="<?php echo $this->get_field_id('zt_rand'); ?>" name="<?php echo $this->get_field_name('zt_rand'); ?>" />
			<label for="<?php echo $this->get_field_id('zt_rand'); ?>"> <?php _z('Activate random order'); ?></label>
		</p>
		
	<?php
	}

}
