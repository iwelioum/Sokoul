<?php
/**
 * @since 1.0.0
 * @version 2.0
 */

/**
 * @since 3.4.0
 * @version 2.0
 */
CSF::createSection(ZETA_OPTIONS,
    array(
        'title'  => __z('Multi Featured'),
        'parent' => 'homepage',
        'icon'   => 'fa fa-minus',
        'fields' => array(
            array(
                'id'      => 'featuredmulti_title',
                'type'    => 'text',
                'title'   => __z('Module Title'),
                'default' => __z('Featured'),
                'subtitle'    => __z('Add title to show')
            ),
            array(
                'type'    => 'subheading',
                'content' => __z('Sub-Modules')
            ),
            array(
                'id'    => 'featured_multi',
                'type'  => 'sorter',
                'default' => array(
                    'enabled' => array(
						'featured-multi-popular' 			=> __z('Popular'),
                        'featured-multi-topimdb'        => __z('Top IMDb'),
                        'featured-multi-toprated'       => __z('Top Rated')

                    ),
                    'disabled' => array(
						'featured-multi-random' 			=> __z('Random'),
						'featured-multi-year' 			=> __z('This Year'),								
					),
                ),
                'enabled_title'  => __z('Sub-Modules enabled'),
                'disabled_title' => __z('Sub-Modules disabled'),
            ),
 
			
			array(
  'id'            => 'featured_multisub',
  'type'          => 'accordion',

  'accordions'          => array(
    array(
      'title'     => 'Popular',
      'fields'    => array(
            array(
                'id'      => 'featured-multi-populartit',
                'type'    => 'text',
                'title'   => __z('Module Title'),
                'default' => __z('Popular'),
                'subtitle' => __z('Add title to show')
            ),
			array(
				  'id'          => 'featured-multi-popularpage',
				  'type'        => 'select',
				  'title'       => 'Archive Page',
				  'placeholder' => 'Select a page',
				  'options'     => 'pages',
				  'query_args'  => array(
					'posts_per_page' => -1 
				 )
			),
			array(
                'id'      => 'featured-multi-popularite',
                'type'    => 'text',
                'title'   => __z('Items number'),
                'subtitle' => __z('Number of items to show'),
                'default' => '18',
                'attributes' => array(
                    'type' => 'number',
                    'style' => 'width:180px'
                )
            ),

      )
    ),
    array(
      'title'     => 'Top IMDb',
      'fields'    => array(
            array(
                'id'      => 'featured-multi-topimdbtit',
                'type'    => 'text',
                'title'   => __z('Module Title'),
                'default' => __z('Top IMDb'),
                'subtitle' => __z('Add title to show')
            ),
			array(
				  'id'          => 'featured-multi-topimdbpage',
				  'type'        => 'select',
				  'title'       => 'Archive Page',
				  'placeholder' => 'Select a page',
				  'options'     => 'pages',
				  'query_args'  => array(
					'posts_per_page' => -1 
				 )
			),
            array(
                'id'      => 'featured-multi-topimdbite',
                'type'    => 'text',
                'title'   => __z('Items number'),
                'subtitle' => __z('Number of items to show'),
                'default' => '18',
                'attributes' => array(
                    'type' => 'number',
                    'style' => 'width:180px'
                )
            ),

      )
    ),
    array(
      'title'     => 'Top Rated',
      'fields'    => array(
            array(
                'id'      => 'featured-multi-topratedtit',
                'type'    => 'text',
                'title'   => __z('Module Title'),
                'default' => __z('Top IMDb'),
                'subtitle' => __z('Add title to show')
            ),
			array(
				  'id'          => 'featured-multi-topratedpage',
				  'type'        => 'select',
				  'title'       => 'Archive Page',
				  'placeholder' => 'Select a page',
				  'options'     => 'pages',
				  'query_args'  => array(
					'posts_per_page' => -1 
				 )
			),
            array(
                'id'      => 'featured-multi-topratedite',
                'type'    => 'text',
                'title'   => __z('Items number'),
                'subtitle' => __z('Number of items to show'),
                'default' => '18',
                'attributes' => array(
                    'type' => 'number',
                    'style' => 'width:180px'
                )
            ),
      )
    ),
    array(
      'title'     => 'Random',
      'fields'    => array(
            array(
                'id'      => 'featured-multi-randomtit',
                'type'    => 'text',
                'title'   => __z('Module Title'),
                'default' => __z('Random'),
                'subtitle' => __z('Add title to show')
            ),
			array(
				  'id'          => 'featured-multi-randompage',
				  'type'        => 'select',
				  'title'       => 'Archive Page',
				  'placeholder' => 'Select a page',
				  'options'     => 'pages',
				  'query_args'  => array(
					'posts_per_page' => -1 
				 )
			),
            array(
                'id'      => 'randomitems',
                'type'    => 'text',
                'title'   => __z('Items number'),
                'subtitle' => __z('Number of items to show'),
                'default' => '18',
                'attributes' => array(
                    'type' => 'number',
                    'style' => 'width:180px'
                )
            ),
      )
    ),
    array(
      'title'     => 'This Year',
      'fields'    => array(
            array(
                'id'      => 'featured-multi-yeartit',
                'type'    => 'text',
                'title'   => __z('Module Title'),
                'default' => __z('This Year'),
                'subtitle' => __z('Add title to show')
            ),
			array(
				  'id'          => 'featured-multi-yearpage',
				  'type'        => 'select',
				  'title'       => 'Archive Page',
				  'placeholder' => 'Select a page',
				  'options'     => 'pages',
				  'query_args'  => array(
					'posts_per_page' => -1 
				 )
			),
            array(
                'id'      => 'featured-multi-randomite',
                'type'    => 'text',
                'title'   => __z('Items number'),
                'subtitle' => __z('Number of items to show'),
                'default' => '18',
                'attributes' => array(
                    'type' => 'number',
                    'style' => 'width:180px'
                )
            ),
      )
    ),
  ),
  'default'       => array(
    'opt-text-1'  => 'This is text 1 value',
    'opt-text-2'  => 'This is text 2 value',
    'opt-color-1' => '#555',
    'opt-color-2' => '#999',
  )
),
			
			
			
			
			
			
			
			
			
			
			
			
			
			
			
			
			
			
			
			

        )
    )
);
